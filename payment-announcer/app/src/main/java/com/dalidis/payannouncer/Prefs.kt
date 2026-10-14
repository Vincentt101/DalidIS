package com.dalidis.payannouncer

import android.content.Context

/** Thin wrapper over SharedPreferences, shared by the settings screen and the listener. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("payment_announcer", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Package names of the apps whose notifications are announced. */
    var watchedPackages: Set<String>
        // Copy: the set returned by getStringSet must not be modified.
        get() = sp.getStringSet(KEY_PACKAGES, null)?.toSet() ?: DEFAULT_PACKAGES
        set(value) = sp.edit().putStringSet(KEY_PACKAGES, value.toSet()).apply()

    fun addPackage(pkg: String) {
        watchedPackages = watchedPackages + pkg
    }

    fun removePackage(pkg: String) {
        watchedPackages = watchedPackages - pkg
    }

    /** Last notification text seen from a watched app, shown on the settings screen for debugging. */
    var lastCaptured: String?
        get() = sp.getString(KEY_LAST, null)
        set(value) = sp.edit().putString(KEY_LAST, value).apply()

    var batteryPromptShown: Boolean
        get() = sp.getBoolean(KEY_BATTERY_PROMPT, false)
        set(value) = sp.edit().putBoolean(KEY_BATTERY_PROMPT, value).apply()

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PACKAGES = "watched_packages"
        private const val KEY_LAST = "last_captured"
        private const val KEY_BATTERY_PROMPT = "battery_prompt_shown"

        val DEFAULT_PACKAGES = setOf(
            "com.paypal.android.p2pmobile", // PayPal
            "com.transferwise.android",     // Wise
        )
    }
}
