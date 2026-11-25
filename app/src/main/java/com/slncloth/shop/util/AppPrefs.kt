package com.slncloth.shop.util

import android.content.Context
import android.content.SharedPreferences

enum class AppMode { MAIN, CLIENT }

/**
 * Non-secret configuration. Tokens and session data live in [com.slncloth.shop.security.SecureStore].
 */
class AppPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sln_prefs", Context.MODE_PRIVATE)

    var mode: AppMode
        get() = if (prefs.getString(KEY_MODE, null) == "CLIENT") AppMode.CLIENT else AppMode.MAIN
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    /** True once the shop exists and (on the main phone) the server has been started once. */
    var configured: Boolean
        get() = prefs.getBoolean(KEY_CONFIGURED, false)
        set(value) = prefs.edit().putBoolean(KEY_CONFIGURED, value).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var fingerprint: String
        get() = prefs.getString(KEY_FINGERPRINT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FINGERPRINT, value).apply()

    var serverPort: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    /** Blank = detect automatically. Otherwise an IPv4 literal entered by the owner. */
    var bindAddress: String
        get() = prefs.getString(KEY_BIND, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BIND, value).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    /** Name shown for this phone in the paired-devices list. */
    var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    fun clearConnection() {
        prefs.edit()
            .remove(KEY_BASE_URL)
            .remove(KEY_FINGERPRINT)
            .apply()
    }

    fun resetAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val DEFAULT_PORT = 8787
        private const val KEY_MODE = "mode"
        private const val KEY_CONFIGURED = "configured"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_FINGERPRINT = "fingerprint"
        private const val KEY_PORT = "port"
        private const val KEY_BIND = "bind_address"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_DEVICE_NAME = "device_name"
    }
}
