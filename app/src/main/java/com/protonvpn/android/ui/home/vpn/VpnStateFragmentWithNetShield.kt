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

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.netshield.BottomSheetNetShield
import com.protonvpn.android.netshield.NetShieldComposable
import com.protonvpn.android.ui.home.HomeActivity
import com.protonvpn.android.ui.onboarding.OnboardingDialogs
import com.protonvpn.android.ui.onboarding.OnboardingPreferences
import com.protonvpn.android.ui.planupgrade.UpgradeNetShieldDialogActivity
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import me.proton.core.compose.theme.ProtonTheme
import javax.inject.Inject

abstract class VpnStateFragmentWithNetShield(@LayoutRes layout: Int) : Fragment(layout) {

    protected val parentViewModel: VpnStateViewModel by viewModels(ownerProducer = { requireParentFragment() })

    // All these dependencies are required by the NetShieldSwitch.
    // Once we refactor it, they should be removed.
    @Inject lateinit var appConfig: AppConfig
    @Inject lateinit var vpnStatusProviderUI: VpnStatusProviderUI
    @Inject lateinit var vpnConnectionManager: VpnConnectionManager
    @Inject lateinit var currentUser: CurrentUser
    // End of NetShieldSwitch's dependencies.

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        netShieldComposeView().setContent {
            ProtonTheme {
                NetShieldComposable(parentViewModel.netShieldViewState,
                    navigateToNetShield = {
                        BottomSheetNetShield().show(parentFragmentManager, tag)
                    },
                    navigateToUpgrade = {
                        requireContext().startActivity(Intent(context, UpgradeNetShieldDialogActivity::class.java))
                    })
            }
        }
        netShieldComposeView().isVisible = true

        val onboardingView = netShieldComposeView()
        parentViewModel.bottomSheetFullyExpanded.observe(viewLifecycleOwner, Observer { isExpanded ->
            if (isExpanded) {
                // Once we migrate to Hilt we should be able to inject Tooltips easily.
                val tooltips = (requireActivity() as HomeActivity).tooltips
                OnboardingDialogs.showDialogOnView(
                    tooltips, onboardingView, onboardingView,
                    getString(R.string.onboarding_netshield_title), getString(R.string.onboardingNetshield),
                    OnboardingPreferences.NETSHIELD_DIALOG
                )
            }
        })
    }

    protected abstract fun netShieldComposeView(): ComposeView
}
