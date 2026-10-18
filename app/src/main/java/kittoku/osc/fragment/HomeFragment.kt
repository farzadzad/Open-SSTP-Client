package kittoku.osc.fragment

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kittoku.osc.R
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.checkPreferences
import kittoku.osc.preference.custom.HomeConnectorPreference
import kittoku.osc.preference.toastInvalidSetting
import kittoku.osc.service.ACTION_VPN_CONNECT
import kittoku.osc.service.ACTION_VPN_DISCONNECT
import kittoku.osc.service.SstpVpnService
import java.security.MessageDigest


class HomeFragment : PreferenceFragmentCompat() {
    private val SECRET = "CHANGE_ME_123"

    private val preparationLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService(ACTION_VPN_CONNECT)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.home, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        attachConnectorListener()
    }

    private fun startVpnService(action: String) {
        val intent = Intent(requireContext(), SstpVpnService::class.java).setAction(action)

        if (action == ACTION_VPN_CONNECT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }
    }

    private fun isLicenseValid(): Boolean {
        return System.currentTimeMillis() < preferenceManager.sharedPreferences!!.getLong("LICENSE_EXPIRY", 0L)
    }

    private fun deviceId(): String {
        return Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    private fun sign(days: String, deadline: String): String {
        val text = deviceId() + "|" + days + "|" + deadline + "|" + SECRET
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(6)
    }

    private fun toast(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
    }

    private fun activate(code: String) {
        val prefs = preferenceManager.sharedPreferences!!
        val parts = code.trim().lowercase().split("-")
        if (parts.size != 3) { toast("Invalid code"); return }

        val days = parts[0].toLongOrNull()
        val deadlineDay = parts[1].toLongOrNull()

        if (days == null || days <= 0 || deadlineDay == null || sign(parts[0], parts[1]) != parts[2]) {
            toast("Invalid code")
            return
        }

        // deadlineDay = number of days after 2026-01-01
        val deadlineMs = 1767225600000L + (deadlineDay + 1) * 86400000L
        if (System.currentTimeMillis() > deadlineMs) {
            toast("Code expired")
            return
        }

        if (prefs.getString("LAST_CODE", "") == code.trim().lowercase()) {
            toast("Code already used")
            return
        }

        val expiry = System.currentTimeMillis() + days * 86400000L
        prefs.edit().putLong("LICENSE_EXPIRY", expiry).putString("LAST_CODE", code.trim().lowercase()).apply()
        toast("Activated for $days days. Turn the switch on.")
    }

    private fun showCodeDialog() {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = "30-364-xxxxxx"

        AlertDialog.Builder(requireContext())
            .setTitle("Activation code")
            .setMessage("Device ID: " + deviceId())
            .setView(input)
            .setPositiveButton("OK") { _, _ -> activate(input.text.toString()) }
            .setNeutralButton("Copy ID") { _, _ ->
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("id", deviceId()))
                toast("Device ID copied")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun attachConnectorListener() {
        findPreference<HomeConnectorPreference>(OscPrefKey.HOME_CONNECTOR.name)!!.also {
            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newState ->
                if (newState == true) {
                    if (!isLicenseValid()) {
                        showCodeDialog()
                        return@OnPreferenceChangeListener false
                    }

                    checkPreferences(preferenceManager.sharedPreferences!!)?.also { message ->
                        toastInvalidSetting(message, requireContext())
                        return@OnPreferenceChangeListener false
                    }

                    VpnService.prepare(requireContext())?.also { intent ->
                        preparationLauncher.launch(intent)
                    } ?: startVpnService(ACTION_VPN_CONNECT)
                } else {
                    startVpnService(ACTION_VPN_DISCONNECT)
                }

                true
            }
        }
    }
}
