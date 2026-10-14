package com.dalidis.payannouncer

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Bound by the system once the user grants Notification Access. Because the system holds
 * the binding, this service keeps running in the background without a foreground service.
 */
class PaymentNotificationListener : NotificationListenerService() {

    private lateinit var speaker: Speaker
    private lateinit var prefs: Prefs

    /** Notification key+text -> time last announced, to avoid repeating updated/re-posted notifications. */
    private val recentlySpoken = LinkedHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        speaker = Speaker(this)
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        Log.i(TAG, "Listener connected")
    }

    override fun onListenerDisconnected() {
        // Ask the system to bind us again (e.g. after it was killed under memory pressure).
        Log.w(TAG, "Listener disconnected, requesting rebind")
        NotificationListenerService.requestRebind(ComponentName(this, PaymentNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!prefs.enabled) return
        if (sbn.packageName !in prefs.watchedPackages) return

        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n") { it.toString() }
        val body = listOf(bigText, text, lines).firstOrNull { !it.isNullOrBlank() }

        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        if (isDuplicate("${sbn.packageName}|$title|$body")) return

        val payment = PaymentParser.parse(
            listOf(bigText, text, lines, title, listOfNotNull(title, body).joinToString(" "))
        )
        // Only incoming money is announced. Everything else (promos, OTPs, outgoing
        // transfers, bills) stays silent but is still logged so missed wordings can be added.
        val utterance = payment?.toSpeech()

        prefs.lastCaptured = buildString {
            append(appLabel(this@PaymentNotificationListener, sbn.packageName))
            append("\nTitle: ").append(title.orEmpty())
            append("\nText: ").append(body.orEmpty())
            append("\nSpoken: ").append(utterance ?: "(nothing - not an incoming payment)")
        }
        if (utterance != null) speaker.speak(utterance)
    }

    private fun isDuplicate(key: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        recentlySpoken.entries.removeAll { now - it.value > DEDUPE_WINDOW_MS }
        if (recentlySpoken.containsKey(key)) return true
        recentlySpoken[key] = now
        return false
    }

    companion object {
        private const val TAG = "PaymentListener"
        private const val DEDUPE_WINDOW_MS = 30_000L

        fun appLabel(context: Context, pkg: String): String = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }
}
