/*
 * Copyright (c) 2023 Proton Technologies AG
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.appconfig.RestrictionsConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.tickFlow
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.Reusable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.entity.FeatureId
import me.proton.core.featureflag.domain.repository.FeatureFlagRepository
import me.proton.core.network.domain.ApiException
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

private const val ALLOW_CHANGE_SERVER_CONNECT_DELAY_MS = 6_000L

@Reusable
class UnrestrictedChangeServerOnLongConnectEnabled @Inject constructor(
    currentUser: CurrentUser,
    private val featureFlagRepository: FeatureFlagRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val isEnabled = currentUser.userFlow.flatMapLatest { user ->
        if (user == null) flowOf(false)
        else getFeatureFlagFlow(user.userId, FEATURE_ID)
    }

    private fun getFeatureFlagFlow(userId: UserId, featureId: FeatureId) =
        featureFlagRepository
            .observe(userId, featureId, refresh = false)
            .map { flag -> flag?.value ?: false }
            .catch { e ->
                if (e !is ApiException) throw e
                emit(false)
            }

    companion object {
        private val FEATURE_ID = FeatureId("UnrestrictedChangeServerOnLongConnect")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChangeServerViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    vpnStatusProviderUI: VpnStatusProviderUI,
    private val restrictConfig: RestrictionsConfig,
    private val vpnConnectionManager: VpnConnectionManager,
    private val serverManager: ServerManager,
    private val changeServerPrefs: ChangeServerPrefs,
    private val upgradeTelemetry: UpgradeTelemetry,
    isUnrestrictedChangeServerEnabled: UnrestrictedChangeServerOnLongConnectEnabled,
    @WallClock private val clock: () -> Long,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val hasTroubleConnecting = isUnrestrictedChangeServerEnabled.isEnabled.flatMapLatest { isEnabled ->
        if (isEnabled) hasTroubleConnectingFlow
        else flowOf(false)
    }.stateIn(mainScope, SharingStarted.Lazily, false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val hasTroubleConnectingFlow = vpnStatusProviderUI.status
        .distinctUntilChanged { old, new ->
            old.isActivelyEstablishingConnection() == new.isActivelyEstablishingConnection()
                && old.connectionParams?.profile == new.connectionParams?.profile
        }
        .transformLatest { status ->
            emit(false)
            if (status.isActivelyEstablishingConnection()) {
                delay(ALLOW_CHANGE_SERVER_CONNECT_DELAY_MS)
                emit(true)
            }
        }

    private fun restrictedStateFlow(
        changeServerPrefs: ChangeServerPrefs,
    ) = combine(
        changeServerPrefs.lastChangeTimestampFlow,
        changeServerPrefs.changeCounterFlow,
        tickFlow(1.seconds, clock),
        restrictConfig.restrictionFlow
    ) { lastChangeTimestamp, actionCounter, timestamp, restrictions ->
        val elapsedSeconds = (timestamp - lastChangeTimestamp) / 1000
        val delayInSeconds =
            if (changeServerPrefs.changeCounter == restrictConfig.changeServerConfig().maxAttemptCount) restrictions.changeServerConfig.longDelayInSeconds else restrictions.changeServerConfig.shortDelayInSeconds
        val remainingCooldown = delayInSeconds - elapsedSeconds

        if (remainingCooldown > 0) {
            ChangeServerViewState.Locked(
                formatTime(remainingCooldown.toInt()),
                remainingCooldown.toInt(),
                delayInSeconds,
                actionCounter == restrictConfig.changeServerConfig().maxAttemptCount
            )
        } else {
            ChangeServerViewState.Unlocked
        }
    }

    val state: StateFlow<ChangeServerViewState> =
        restrictConfig.restrictionFlow.flatMapLatest { restrictions ->
            if (!restrictions.quickConnect) {
                flowOf(ChangeServerViewState.Hidden)
            } else {
                restrictedStateFlow(changeServerPrefs)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ChangeServerViewState.Unlocked)

    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    fun changeServer(vpnUiDelegate: VpnUiDelegate, dontUpdateCounters: Boolean = false) {
        mainScope.launch {
            vpnConnectionManager.connect(
                vpnUiDelegate,
                serverManager.randomProfile,
                ConnectTrigger.QuickConnect("Change server")
            )
            // Delay to not show instant locked state before actual connection
            delay(500)

            if (!dontUpdateCounters) {
                val currentCount = changeServerPrefs.changeCounter + 1
                changeServerPrefs.changeCounter =
                    if (currentCount > restrictConfig.changeServerConfig().maxAttemptCount) 0 else currentCount
                changeServerPrefs.lastChangeTimestamp = System.currentTimeMillis()
            }
        }
    }

    fun onUpgradeModalOpened() {
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.CHANGE_SERVER)
    }
}

sealed class ChangeServerViewState {
    object Hidden : ChangeServerViewState()
    object Unlocked : ChangeServerViewState()
    data class Locked(
        val remainingTimeText: String,
        val remainingSeconds: Int,
        val totalCooldownSeconds: Int,
        val isFullLocked: Boolean,
    ) : ChangeServerViewState() {
        val progress = remainingSeconds.toFloat() / totalCooldownSeconds
    }
}

private fun VpnStateMonitor.Status.isActivelyEstablishingConnection() =
    state.isEstablishingConnection && state != VpnState.WaitingForNetwork
