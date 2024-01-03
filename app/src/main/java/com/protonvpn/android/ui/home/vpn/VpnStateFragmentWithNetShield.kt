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
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.netshield.NetShieldComposable
import com.protonvpn.android.netshield.UpgradePromo
import com.protonvpn.android.ui.planupgrade.UpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeNetShieldHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesHighlightsFragment
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import me.proton.core.compose.theme.ProtonTheme3
import javax.inject.Inject

@Deprecated("To be removed with old UI")
abstract class VpnStateFragmentWithNetShield(@LayoutRes layout: Int) : Fragment(layout) {

    protected val parentViewModel: VpnStateViewModel by viewModels(ownerProducer = { requireParentFragment() })
    private val changeServerViewModel: ChangeServerViewModel by viewModels(ownerProducer = { requireParentFragment() })

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
            val state = changeServerViewModel.state.collectAsStateWithLifecycle()
            ProtonTheme3 {
                if (state.value is ChangeServerViewState.Locked) {
                    UpgradePromo(titleRes = R.string.not_wanted_country_title, descriptionRes = R.string.not_wanted_country_description, iconRes = R.drawable.upsell_worldwide_cover_exclamation) {
                        UpgradeDialogActivity.launch<UpgradePlusCountriesHighlightsFragment>(requireContext())
                    }
                } else {
                    NetShieldComposable(
                        netShieldViewState = parentViewModel.netShieldViewState.collectAsStateWithLifecycle().value,
                        navigateToUpgrade = {
                            UpgradeDialogActivity.launch<UpgradeNetShieldHighlightsFragment>(requireContext())
                        },
                        onNavigateToSubsetting = {})
                }
            }
        }
        netShieldComposeView().isVisible = true
    }

    protected abstract fun netShieldComposeView(): ComposeView
}
