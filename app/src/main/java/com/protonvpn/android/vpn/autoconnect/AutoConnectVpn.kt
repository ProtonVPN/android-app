/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.vpn.autoconnect

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.tv.vpn.createIntentForDefaultProfile
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.Reusable
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@Reusable
class AutoConnectVpn @Inject constructor(
    private val vpnConnectionManager: dagger.Lazy<VpnConnectionManager>,
    private val vpnUiStatus: VpnStatusProviderUI,
    private val effectiveUserSettings: EffectiveCurrentUserSettings,
    private val tvProfileManager: ProfileManager,
    private val serverManager: ServerManager,
    private val currentUser: CurrentUser,
) {
    suspend operator fun invoke() {
        val settings = effectiveUserSettings.effectiveSettings.first()
        if (settings.tvAutoConnectOnBoot && !vpnUiStatus.isEstablishingOrConnected) {
            val profile = tvProfileManager.getDefaultOrFastest()
            val intent = createIntentForDefaultProfile(serverManager, currentUser, settings.protocol, profile)
            vpnConnectionManager.get().connectInBackground(
                intent,
                ConnectTrigger.Auto("Auto-connect on boot")
            )
        }
    }
}
