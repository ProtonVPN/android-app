/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.restrictonsupsell

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.utils.flatMapLatestFreeUser
import com.protonvpn.android.utils.flatMapLatestIfEnabled
import com.protonvpn.android.vpn.VpnConnectionRestriction
import com.protonvpn.android.vpn.VpnConnectionRestrictions
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.filter
import javax.inject.Inject

@Reusable
class StreamingUpsellRestrictionsFlow @Inject constructor(
    isStreamingRestrictionUpsellEnabled: IsStreamingRestrictionUpsellEnabled,
    vpnStateMonitor: VpnStateMonitor,
    private val currentUser: CurrentUser,
) : Flow<VpnConnectionRestrictions> {

    private val flow = isStreamingRestrictionUpsellEnabled.flatMapLatestIfEnabled {
        currentUser.vpnUserFlow.flatMapLatestFreeUser {
            vpnStateMonitor.eventRestrictions.filter {
                it.restrictions.contains(VpnConnectionRestriction.Streaming)
            }
        }
    }

    override suspend fun collect(collector: FlowCollector<VpnConnectionRestrictions>) {
        flow.collect(collector)
    }
}
