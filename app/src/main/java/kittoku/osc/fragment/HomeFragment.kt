package kittoku.osc.fragment

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import kittoku.osc.service.License
import kittoku.osc.service.SstpVpnService


class HomeFragment : PreferenceFragmentCompat() {
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

        License.applyLocks(preferenceManager.sharedPreferences!!)
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

    private fun toast(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
    }

    private fun showCodeDialog() {
        val prefs = preferenceManager.sharedPreferences!!
        val id = License.deviceId(requireContext(), prefs)

        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_PHONE
        input.hint = "30-1234"

        AlertDialog.Builder(requireContext())
            .setTitle("Activation code")
            .setMessage("ID: " + id)
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                toast(License.activate(requireContext(), prefs, input.text.toString()))
            }
            .setNeutralButton("Copy ID") { _, _ ->
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("id", id))
                toast("ID copied")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun attachConnectorListener() {
        findPreference<HomeConnectorPreference>(OscPrefKey.HOME_CONNECTOR.name)!!.also {
            it.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newState ->
                if (newState == true) {
                    if (!License.isValid(preferenceManager.sharedPreferences!!)) {
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
