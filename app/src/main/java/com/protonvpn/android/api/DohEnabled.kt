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

import android.util.Log
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsFlow
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val NO_DOH_STATES = listOf(VpnState.Connected, VpnState.Connecting)

@Singleton
@Suppress("UseDataClass")
class DohEnabled @Inject constructor() {

    private val isEnabledDeferred: CompletableDeferred<Boolean> = CompletableDeferred()

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke() = if (isEnabledDeferred.isCompleted) {
        isEnabledDeferred.getCompleted()
    } else runBlocking {
        Log.w("DohEnabled", "blocking read")
        isEnabledDeferred.await()
    }

    private fun set(isEnabled: Boolean) {
        isEnabledDeferred.complete(isEnabled)
    }

    // DohEnabled is used by VpnApiClient and therefore it cannot depend directly on user settings because this would
    // create cyclic dependencies (AccountManager depends indirectly on ApiClient and user settings require
    // AccountManager).
    // Use this Provider to break the cycle and push the value to DohEnabled.
    @Singleton
    @Suppress("UseDataClass")
    class Provider @Inject constructor(
        mainScope: CoroutineScope,
        dispatcherProvider: VpnDispatcherProvider,
        private val dohEnabled: DohEnabled,
        effectiveCurrentUserSettingsFlow: EffectiveCurrentUserSettingsFlow,
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
    }
}
