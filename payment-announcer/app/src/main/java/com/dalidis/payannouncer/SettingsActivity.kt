package com.dalidis.payannouncer

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dalidis.payannouncer.databinding.ActivitySettingsBinding
import com.dalidis.payannouncer.databinding.ItemWatchedAppBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs
    private lateinit var speaker: Speaker
    private val adapter = WatchedAppsAdapter()

    private val listenerComponent by lazy {
        ComponentName(this, PaymentNotificationListener::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)
        speaker = Speaker(this)

        binding.enabledSwitch.isChecked = prefs.enabled
        binding.enabledSwitch.setOnCheckedChangeListener { _, checked -> prefs.enabled = checked }

        binding.notificationAccessButton.setOnClickListener { openNotificationAccessSettings() }
        binding.appInfoButton.setOnClickListener { openAppInfo() }
        binding.batteryButton.setOnClickListener { requestIgnoreBatteryOptimizations() }
        binding.testVoiceButton.setOnClickListener {
            speaker.speak(PaymentParser.Payment("$350", "Test Sender").toSpeech())
        }
        binding.addAppButton.setOnClickListener { showAppPicker() }

        binding.appsList.layoutManager = LinearLayoutManager(this)
        binding.appsList.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        adapter.submit(prefs.watchedPackages)
        binding.lastCaptured.text = prefs.lastCaptured ?: getString(R.string.last_captured_none)

        if (hasNotificationAccess()) {
            // Nudge the system to (re)bind the listener, e.g. after an app update or force stop.
            NotificationListenerService.requestRebind(listenerComponent)
            maybePromptBatteryOptimization()
        }
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    // ---- Status -------------------------------------------------------------------------

    private fun hasNotificationAccess() =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun isIgnoringBatteryOptimizations() =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun refreshStatus() {
        val access = hasNotificationAccess()
        binding.notificationAccessStatus.text =
            getString(if (access) R.string.status_access_granted else R.string.status_access_missing)
        binding.notificationAccessStatus.setTextColor(statusColor(access))
        // Android 13+ blocks Notification Access for sideloaded apps until "Allow restricted
        // settings" is enabled from App info, so surface that path when access is missing.
        val showRestrictedHint = !access && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        binding.restrictedHint.visibility = if (showRestrictedHint) View.VISIBLE else View.GONE
        binding.appInfoButton.visibility = if (showRestrictedHint) View.VISIBLE else View.GONE

        val unrestricted = isIgnoringBatteryOptimizations()
        binding.batteryStatus.text =
            getString(if (unrestricted) R.string.status_battery_ok else R.string.status_battery_optimized)
        binding.batteryStatus.setTextColor(statusColor(unrestricted))
        binding.batteryButton.isEnabled = !unrestricted
    }

    private fun statusColor(ok: Boolean) =
        ContextCompat.getColor(this, if (ok) R.color.status_ok else R.color.status_bad)

    // ---- System settings ----------------------------------------------------------------

    private fun openNotificationAccessSettings() {
        // Android 11+ can deep-link straight to this app's toggle.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    listenerComponent.flattenToString()
                )
            if (tryStart(detail)) return
        }
        tryStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openAppInfo() {
        tryStart(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
    }

    private fun maybePromptBatteryOptimization() {
        if (isIgnoringBatteryOptimizations() || prefs.batteryPromptShown) return
        prefs.batteryPromptShown = true
        AlertDialog.Builder(this)
            .setTitle(R.string.battery_dialog_title)
            .setMessage(R.string.battery_dialog_message)
            .setPositiveButton(R.string.battery_dialog_allow) { _, _ -> requestIgnoreBatteryOptimizations() }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    @SuppressLint("BatteryLife") // Sideloaded app; the direct prompt is the point.
    private fun requestIgnoreBatteryOptimizations() {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        if (!tryStart(direct)) {
            tryStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    // ---- Watched apps -------------------------------------------------------------------

    private data class AppEntry(val label: String, val pkg: String)

    private fun showAppPicker() {
        val watched = prefs.watchedPackages
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcherIntent, 0)
            .map { AppEntry(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
            .filter { it.pkg != packageName && it.pkg !in watched }
            .sortedBy { it.label.lowercase() }

        AlertDialog.Builder(this)
            .setTitle(R.string.pick_app_title)
            .setItems(apps.map { "${it.label}\n${it.pkg}" }.toTypedArray()) { _, which ->
                addPackage(apps[which].pkg)
            }
            .setNeutralButton(R.string.enter_package_manually) { _, _ -> showManualEntry() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showManualEntry() {
        val input = EditText(this).apply {
            hint = "com.example.bank"
            setSingleLine()
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.enter_package_manually)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val pkg = input.text.toString().trim()
                if (pkg.matches(Regex("""[A-Za-z][\w]*(\.[A-Za-z_][\w]*)+"""))) {
                    addPackage(pkg)
                } else {
                    Toast.makeText(this, R.string.invalid_package, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addPackage(pkg: String) {
        prefs.addPackage(pkg)
        adapter.submit(prefs.watchedPackages)
    }

    private fun removePackage(pkg: String) {
        prefs.removePackage(pkg)
        adapter.submit(prefs.watchedPackages)
    }

    private inner class WatchedAppsAdapter : RecyclerView.Adapter<WatchedAppsAdapter.Holder>() {

        private var items: List<String> = emptyList()

        @SuppressLint("NotifyDataSetChanged") // Tiny list.
        fun submit(packages: Set<String>) {
            items = packages.sortedBy { PaymentNotificationListener.appLabel(this@SettingsActivity, it).lowercase() }
            binding.emptyApps.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            notifyDataSetChanged()
        }

        inner class Holder(val item: ItemWatchedAppBinding) : RecyclerView.ViewHolder(item.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemWatchedAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val pkg = items[position]
            val installed = try {
                holder.item.icon.setImageDrawable(packageManager.getApplicationIcon(pkg))
                true
            } catch (e: Exception) {
                holder.item.icon.setImageResource(R.drawable.ic_app_placeholder)
                false
            }
            holder.item.label.text = PaymentNotificationListener.appLabel(this@SettingsActivity, pkg)
            holder.item.packageName.text =
                if (installed) pkg else getString(R.string.not_installed, pkg)
            holder.item.removeButton.setOnClickListener { removePackage(pkg) }
        }
    }
}
