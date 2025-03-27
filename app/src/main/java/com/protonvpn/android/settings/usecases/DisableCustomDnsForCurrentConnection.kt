/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.settings.usecases

import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@Reusable
class DisableCustomDnsForCurrentConnection @Inject constructor(
    private val mainScope: CoroutineScope,
    private val profilesDao: ProfilesDao,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
) {
    // Returns true if reconnection is needed to apply the change
    operator fun invoke(): Boolean {
        val profileId = vpnStatusProviderUI.connectionIntent?.profileId
        mainScope.launch {
            if (profileId != null) {
                profilesDao.disableCustomDNS(profileId)
            } else {
                userSettingsManager.disableCustomDNS()
            }
        }
        return vpnStatusProviderUI.isEstablishingOrConnected
    }
}