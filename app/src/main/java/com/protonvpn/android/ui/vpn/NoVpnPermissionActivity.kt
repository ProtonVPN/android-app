/*
 * Copyright (c) 2021. Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.ui.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityNoVpnPermissionBinding
import com.protonvpn.android.databinding.FragmentNoVpnPermissionDisableAlwaysOnBinding
import com.protonvpn.android.databinding.FragmentNoVpnPermissionGrantBinding
import com.protonvpn.android.databinding.FragmentNoVpnPermissionMainBinding
import com.protonvpn.android.redesign.recents.data.ConnectIntentData
import com.protonvpn.android.redesign.recents.data.toAnyConnectIntent
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.ui.vpn.NoVpnPermissionActivity.Companion.EXTRA_RECONNECT_INTENT
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.ViewUtils.viewBinding
import com.protonvpn.android.utils.openVpnSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NoVpnPermissionActivity : BaseActivityV2() {

    private val binding by viewBinding(ActivityNoVpnPermissionBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initToolbarWithUpEnabled(binding.contentAppbar.toolbar)

        if (savedInstanceState == null) {
            // Older Android versions don't have the "Always-on VPN" setting. In this case show
            // only the "grant permission" screen.
            val fragment =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) NoVpnPermissionMainFragment()
                else NoVpnPermissionGrantFragment()
            supportFragmentManager.commitNow {
                add(R.id.fragmentContainer, fragment)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = if (item.itemId == android.R.id.home) {
        onBackPressed()
        true
    } else {
        super.onOptionsItemSelected(item)
    }

    companion object {

        const val EXTRA_RECONNECT_INTENT = "ConnectIntent"

        class Contract : ActivityResultContract<AnyConnectIntent, AnyConnectIntent?>() {
            override fun createIntent(context: Context, input: AnyConnectIntent) =
                Intent(context, NoVpnPermissionActivity::class.java).apply {
                    putExtra(EXTRA_RECONNECT_INTENT, input.toData())
                }

            override fun parseResult(resultCode: Int, intent: Intent?): AnyConnectIntent? =
                (intent?.getSerializableExtra(EXTRA_RECONNECT_INTENT) as? ConnectIntentData)?.toAnyConnectIntent()
        }
    }
}

abstract class NoVpnPermissionFragmentBase(
    @LayoutRes layoutId: Int,
    @StringRes private val title: Int?
) : Fragment(layoutId) {

    @Inject lateinit var serverManager: ServerManager

    override fun onResume() {
        super.onResume()
        requireActivity().title = if (title == null) "" else getString(title)
    }

    protected fun reconnect() {
        with(requireActivity()) {
            val profile = intent?.getSerializableExtra(EXTRA_RECONNECT_INTENT)
            setResult(Activity.RESULT_OK, Intent().apply { putExtra(EXTRA_RECONNECT_INTENT, profile) })
            finish()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class NoVpnPermissionMainFragment :
    NoVpnPermissionFragmentBase(R.layout.fragment_no_vpn_permission_main, null) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(FragmentNoVpnPermissionMainBinding.bind(view)) {
            layoutGrantPermissions.setOnClickListener {
                openFragment(NoVpnPermissionGrantFragment())
            }
            layoutDisableAlwaysOn.setOnClickListener {
                openFragment(NoVpnPermissionDisableAlwaysOnFragment())
            }
            buttonReconnect.setOnClickListener {
                reconnect()
            }
        }
    }

    private fun openFragment(fragment: Fragment) {
        parentFragmentManager.commit {
            setCustomAnimations(
                R.anim.slide_in_from_end,
                R.anim.slide_out_to_start,
                R.anim.slide_in_from_start,
                R.anim.slide_out_to_end
            )
            replace(R.id.fragmentContainer, fragment)
            addToBackStack(null)
        }
    }
}

@AndroidEntryPoint
class NoVpnPermissionGrantFragment : NoVpnPermissionFragmentBase(
    R.layout.fragment_no_vpn_permission_grant,
    R.string.noVpnPermissionGrantPermissionTitle
) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentNoVpnPermissionGrantBinding.bind(view)
        binding.buttonReconnect.setOnClickListener {
            reconnect()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class NoVpnPermissionDisableAlwaysOnFragment : NoVpnPermissionFragmentBase(
    R.layout.fragment_no_vpn_permission_disable_always_on,
    R.string.noVpnPermissionDisableAlwaysOnTitle
) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentNoVpnPermissionDisableAlwaysOnBinding.bind(view)
        binding.buttonOpenVpnSettings.setOnClickListener {
            requireActivity().openVpnSettings()
        }
        binding.buttonReconnect.setOnClickListener {
            reconnect()
        }
    }
}
