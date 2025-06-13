/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.api

import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsFlow
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val NO_DOH_STATES = listOf(VpnState.Connected, VpnState.Connecting)

@Singleton
@Suppress("UseDataClass")
class DohEnabled @Inject constructor() {

    private val isEnabled = MutableStateFlow<Boolean?>(null)

    // TODO(VPNAND-2241): simplify this code.
    operator fun invoke(): Boolean = isEnabled.value ?: runBlocking { isEnabled.filterNotNull().first() }

    private fun set(isEnabled: Boolean) {
        this.isEnabled.value = isEnabled
    }

    // DohEnabled is used by VpnApiClient and therefore it cannot depend directly on user settings because this would
    // create cyclic dependencies (AccountManager depends indirectly on ApiClient and user settings require
    // AccountManager).
    // Use this Provider to break the cycle and push the value to DohEnabled.
    @Singleton
    @Suppress("UseDataClass")
    class Provider(
        mainScope: CoroutineScope,
        dispatcherProvider: VpnDispatcherProvider,
        private val dohEnabled: DohEnabled,
        effectiveCurrentUserSettingsFlow: Flow<LocalUserSettings>,
        vpnStateMonitor: VpnStateMonitor
    ) {

        init {
            combine(
                vpnStateMonitor.status,
                effectiveCurrentUserSettingsFlow
            ) { vpnStatus, settings ->
                dohEnabled.set(settings.apiUseDoh && vpnStatus.state !in NO_DOH_STATES)
            }
                .flowOn(dispatcherProvider.Io) // Don't block the main thread.
                .launchIn(mainScope)
        }

        @Inject
        constructor(
            mainScope: CoroutineScope,
            dispatcherProvider: VpnDispatcherProvider,
            dohEnabled: DohEnabled,
            effectiveCurrentUserSettingsFlow: EffectiveCurrentUserSettingsFlow,
            vpnStateMonitor: VpnStateMonitor
        ) : this(
            mainScope,
            dispatcherProvider,
            dohEnabled,
            effectiveCurrentUserSettingsFlow as Flow<LocalUserSettings>,
            vpnStateMonitor
        )
    }
}
