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

package com.protonvpn.android.vpn

import androidx.annotation.VisibleForTesting
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.utils.mapState
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

enum class DnsOverride {
    None, CustomDns, SystemPrivateDns
}

@Reusable
class IsPrivateDnsActiveFlow @VisibleForTesting constructor(
    private val flow: StateFlow<Boolean>,
) : StateFlow<Boolean> by flow {

    @Inject
    constructor(connectivityMonitor: ConnectivityMonitor) : this(
        connectivityMonitor.isPrivateDnsActive.mapState { it == true }
    )
}

@Reusable
class DnsOverrideFlow(
    private val flow: Flow<DnsOverride>,
): Flow<DnsOverride> {

    @Inject
    constructor(
        isPrivateDnsActiveFlow: IsPrivateDnsActiveFlow,
        settingsForConnection: SettingsForConnection,
    ) : this(
        combine(
            isPrivateDnsActiveFlow,
            settingsForConnection.getFlowForCurrentConnection().map { it.connectionSettings.customDns.effectiveEnabled }
        ) { privateDns, customDns ->
            when {
                privateDns -> DnsOverride.SystemPrivateDns
                customDns -> DnsOverride.CustomDns
                else -> DnsOverride.None
            }
        }
    )

    override suspend fun collect(collector: FlowCollector<DnsOverride>) {
        flow.collect(collector)
    }
}
