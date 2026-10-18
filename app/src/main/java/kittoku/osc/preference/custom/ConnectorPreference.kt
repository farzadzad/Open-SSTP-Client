package kittoku.osc.preference.custom

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import kittoku.osc.R
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.accessor.getBooleanPrefValue


internal class HomeConnectorPreference(context: Context, attrs: AttributeSet) : SwitchPreferenceCompat(context, attrs), OscPreference {
    override val oscPrefKey = OscPrefKey.HOME_CONNECTOR
    override val parentKey: OscPrefKey? = null
    override val preferenceTitle = ""
    override fun updateView() {
        isChecked = getBooleanPrefValue(oscPrefKey, sharedPreferences!!)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val on = isChecked
        val circle = holder.findViewById(R.id.connect_circle)
        val label = holder.findViewById(R.id.connect_label) as? TextView

        circle?.background?.mutate()?.setTint(if (on) 0xFF2E9E5B.toInt() else 0xFF9AA0A6.toInt())
        label?.text = if (on) "Connected" else "Disconnected"
        label?.setTextColor(if (on) 0xFF2E9E5B.toInt() else 0xFF9AA0A6.toInt())
    }

    private var listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == oscPrefKey.name) {
            updateView()
        }
    }

    override fun onAttached() {
        sharedPreferences!!.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onDetached() {
        sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
