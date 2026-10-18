package kittoku.osc.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import kittoku.osc.R
import kittoku.osc.databinding.ActivityMainBinding
import kittoku.osc.fragment.HomeFragment
import kittoku.osc.service.License


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        binding.root.fitsSystemWindows = true
        setContentView(binding.root)

        License.applyLocks(PreferenceManager.getDefaultSharedPreferences(this))

        binding.tabBar.visibility = View.GONE

        binding.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 1

            override fun createFragment(position: Int): Fragment = HomeFragment()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }
}
