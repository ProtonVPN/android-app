/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.android.redesign.recents.usecases

import com.protonvpn.android.redesign.recents.ui.IsConnectionFeedbackFeatureFlagEnabled
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.storage.UiStateStorage
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionFeedbackViewState(
    val showFeedback: Boolean,
    val onFeedbackShown: () -> Unit,
    val onFeedbackProvided: (ConnectionFeedback) -> Unit,
)

enum class ConnectionFeedback {
    Negative,
    None,
    Positive,
}

@Reusable
class ObserveConnectionFeedbackViewState @Inject constructor(
    isConnectionFeedbackFeatureFlagEnabled: IsConnectionFeedbackFeatureFlagEnabled,
    effectiveCurrentUserSettings: EffectiveCurrentUserSettings,
    foregroundActivityTracker: ForegroundActivityTracker,
    vpnStatusProvider: VpnStatusProviderUI,
    private val mainScope: CoroutineScope,
    private val uiStateStorage: UiStateStorage,
) {

    private val isConnectionFeedbackEligibleToShowFlow = MutableStateFlow(value = false)

    private val isConnectedFlow = vpnStatusProvider.status
        .map { vpnStatus -> vpnStatus.state == VpnState.Connected }
        .onEach { isConnected ->
            if (!isConnected) isConnectionFeedbackEligibleToShowFlow.update { false }
        }

    private val isConnectionFeedbackAvailableFlow = combine(
        isConnectionFeedbackFeatureFlagEnabled.observe(),
        effectiveCurrentUserSettings.telemetry,
        isConnectedFlow,
        uiStateStorage.state.map { it.connectionFeedback == ConnectionFeedback.None }
    ) { isConnectionFeedbackEnabled, isTelemetryEnabled, isConnected, isConnectionFeedbackPending ->
        isConnectionFeedbackEnabled && isTelemetryEnabled && isConnected && isConnectionFeedbackPending
    }

    init {
        foregroundActivityTracker.foregroundBackgroundTransitionFlow
            .onEach { (wasInForeground, isInForeground) ->
                if (!wasInForeground && isInForeground) {
                    val isAvailable = isConnectionFeedbackAvailableFlow.first()

                    isConnectionFeedbackEligibleToShowFlow.update { isAvailable }
                }
            }
            .launchIn(scope = mainScope)
    }

    operator fun invoke(): Flow<ConnectionFeedbackViewState> = combine(
        isConnectionFeedbackAvailableFlow,
        isConnectionFeedbackEligibleToShowFlow,
    ) { isConnectionFeedbackAvailable, isConnectionFeedbackEligibleToShow ->
        ConnectionFeedbackViewState(
            showFeedback = isConnectionFeedbackAvailable && isConnectionFeedbackEligibleToShow,
            onFeedbackShown = ::onConnectionFeedbackShown,
            onFeedbackProvided = ::onConnectionFeedbackProvided,
        )
    }

    private fun onConnectionFeedbackShown() {
        mainScope.launch {
            uiStateStorage.update { it.copy(hasShownConnectionFeedback = true) }
        }
    }

    private fun onConnectionFeedbackProvided(connectionFeedback: ConnectionFeedback) {
        mainScope.launch {
            uiStateStorage.update { it.copy(connectionFeedback = connectionFeedback) }
        }
    }

}
