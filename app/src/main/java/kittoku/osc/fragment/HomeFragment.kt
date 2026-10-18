package kittoku.osc.fragment

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
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

    private fun sign(days: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest((days + SECRET).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(8)
    }

    private fun showCodeDialog() {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = "30-xxxxxxxx"

        AlertDialog.Builder(requireContext())
            .setTitle("Activation code")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val parts = input.text.toString().trim().split("-")
                val days = parts.getOrNull(0)?.toLongOrNull()
                if (parts.size == 2 && days != null && days > 0 && sign(parts[0]) == parts[1].lowercase()) {
                    val expiry = System.currentTimeMillis() + days * 86400000L
                    preferenceManager.sharedPreferences!!.edit().putLong("LICENSE_EXPIRY", expiry).apply()
                    Toast.makeText(requireContext(), "Activated for $days days. Turn the switch on.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Invalid code", Toast.LENGTH_LONG).show()
                }
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
