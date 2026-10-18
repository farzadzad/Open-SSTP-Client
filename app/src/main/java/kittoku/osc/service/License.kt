package kittoku.osc.service

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import kittoku.osc.preference.OscPrefKey
import java.security.MessageDigest

internal object License {
    private const val SECRET = "@@Ff57365736"
    private const val DAY = 86400000L
    private const val K_EXPIRY = "LICENSE_EXPIRY"
    private const val K_SEEN = "LICENSE_LAST_SEEN"
    private const val K_COUNT = "LICENSE_COUNT"
    private const val K_FAILS = "LICENSE_FAILS"
    private const val K_LOCK = "LICENSE_LOCKED_UNTIL"

    // time never goes backwards (protects against changing the phone clock)
    fun now(prefs: SharedPreferences): Long {
        val real = System.currentTimeMillis()
        val seen = prefs.getLong(K_SEEN, 0L)
        if (real > seen) {
            prefs.edit().putLong(K_SEEN, real).apply()
            return real
        }
        return seen
    }

    fun expiry(prefs: SharedPreferences): Long = prefs.getLong(K_EXPIRY, 0L)

    fun isValid(prefs: SharedPreferences): Boolean = now(prefs) < expiry(prefs)

    fun remainingMs(prefs: SharedPreferences): Long = expiry(prefs) - now(prefs)

    fun deviceId(context: Context, prefs: SharedPreferences): String {
        val android = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        return android + "-" + prefs.getInt(K_COUNT, 0)
    }

    private fun sign(id: String, days: Long): String {
        val h = MessageDigest.getInstance("SHA-256").digest("$id|$days|$SECRET".toByteArray())
        val n = (((h[0].toInt() and 0xFF) shl 16) or ((h[1].toInt() and 0xFF) shl 8) or (h[2].toInt() and 0xFF)) % 10000
        return n.toString().padStart(4, '0')
    }

    // returns a message for the user
    fun activate(context: Context, prefs: SharedPreferences, code: String): String {
        val t = now(prefs)
        if (t < prefs.getLong(K_LOCK, 0L)) return "Too many wrong codes. Try again later."

        val parts = code.trim().split("-")
        val days = parts.getOrNull(0)?.toLongOrNull()
        if (parts.size == 2 && days != null && days in 1..3650 && sign(deviceId(context, prefs), days) == parts[1]) {
            val base = maxOf(t, expiry(prefs))
            prefs.edit()
                .putLong(K_EXPIRY, base + days * DAY)
                .putInt(K_COUNT, prefs.getInt(K_COUNT, 0) + 1)
                .putInt(K_FAILS, 0)
                .apply()
            return "Activated for $days days. Turn the switch on."
        }

        val fails = prefs.getInt(K_FAILS, 0) + 1
        if (fails >= 5) {
            prefs.edit().putInt(K_FAILS, 0).putLong(K_LOCK, t + 3600000L).apply()
            return "Too many wrong codes. Locked for 1 hour."
        }
        prefs.edit().putInt(K_FAILS, fails).apply()
        return "Invalid code"
    }

    // fixed settings: nobody can change them
    fun applyLocks(prefs: SharedPreferences) {
        prefs.edit()
            .putString(OscPrefKey.HOME_USERNAME.name, "vpn")
            .putString(OscPrefKey.HOME_PASSWORD.name, "vpn")
            .putString(OscPrefKey.SSL_PORT.name, "443")
            .putString(OscPrefKey.SSL_VERSION.name, "TLSv1.2")
            .putBoolean(OscPrefKey.SSL_DO_VERIFY.name, false)
            .apply()
    }
}
