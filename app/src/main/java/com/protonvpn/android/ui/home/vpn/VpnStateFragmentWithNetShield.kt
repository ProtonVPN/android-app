/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui.home.vpn

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.components.NetShieldSwitch
import com.protonvpn.android.models.config.NetShieldProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.home.HomeActivity
import com.protonvpn.android.ui.onboarding.OnboardingDialogs
import com.protonvpn.android.ui.onboarding.OnboardingPreferences
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import javax.inject.Inject

abstract class VpnStateFragmentWithNetShield(@LayoutRes layout: Int) : Fragment(layout) {

    protected val parentViewModel: VpnStateViewModel by viewModels(ownerProducer = { requireParentFragment() })

    // All these dependencies are required by the NetShieldSwitch.
    // Once we refactor it, they should be removed.
    @Inject lateinit var userData: UserData
    @Inject lateinit var appConfig: AppConfig
    @Inject lateinit var stateMonitor: VpnStateMonitor
    @Inject lateinit var vpnConnectionManager: VpnConnectionManager
    // End of NetShieldSwitch's dependencies.

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        netShieldSwitch().init(
            userData.netShieldProtocol,
            appConfig,
            viewLifecycleOwner,
            userData,
            stateMonitor,
            vpnConnectionManager
        ) { s: NetShieldProtocol? ->
            userData.netShieldProtocol = s
        }
        netShieldSwitch().onRadiosExpandClicked = { parentViewModel.onNetShieldExpandClicked() }
        parentViewModel.netShieldExpandStatus.asLiveData()
            .observe(viewLifecycleOwner, Observer { netShieldSwitch().radiosExpanded = it })
        userData.netShieldLiveData.observe(viewLifecycleOwner, Observer<NetShieldProtocol> { state ->
            if (state != null) {
                netShieldSwitch().setNetShieldValue(state)
            }
        })

        parentViewModel.bottomSheetFullyExpanded.observe(viewLifecycleOwner, Observer { isExpanded ->
            if (isExpanded) {
                // Once we migrate to Hilt we should be able to inject Tooltips easily.
                val tooltips = (requireActivity() as HomeActivity).tooltips
                OnboardingDialogs.showDialogOnView(
                    tooltips, netShieldSwitch(), netShieldSwitch(),
                    getString(R.string.netshield), getString(R.string.onboardingNetshield),
                    OnboardingPreferences.NETSHIELD_DIALOG
                )
            }
        })
    }

    protected abstract fun netShieldSwitch(): NetShieldSwitch
}
