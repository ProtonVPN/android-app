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
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.lang.System.currentTimeMillis
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChangeServerViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val restrictConfig: RestrictionsConfig,
    private val vpnConnectionManager: VpnConnectionManager,
    private val serverManager: ServerManager,
    private val changeServerPrefs: ChangeServerPrefs,
    private val upgradeTelemetry: UpgradeTelemetry
) : ViewModel() {

    private val tickFlow = changeServerPrefs.lastChangeTimestampFlow.flatMapLatest {
        flow {
            while (true) {
                val now = currentTimeMillis()
                val elapsedS = (now - it) / 1000
                emit(elapsedS)
                delay(it + (elapsedS + 1) * 1000 - now)
            }

        }
    }

    private fun restrictedStateFlow(
        changeServerPrefs: ChangeServerPrefs,
        tickFlow: Flow<Long>
    ) = combine(
        changeServerPrefs.lastChangeTimestampFlow,
        changeServerPrefs.changeCounterFlow,
        tickFlow,
        restrictConfig.restrictionFlow
    ) { lastChangeTimestamp, actionCounter, _, restrictions ->
        val elapsedSeconds = (System.currentTimeMillis() - lastChangeTimestamp) / 1000
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
                restrictedStateFlow(changeServerPrefs, tickFlow)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ChangeServerViewState.Unlocked)

    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    fun changeServer(vpnUiDelegate: VpnUiDelegate) {
        mainScope.launch {
            vpnConnectionManager.connect(
                vpnUiDelegate,
                serverManager.randomProfile,
                ConnectTrigger.QuickConnect("Change server")
            )
            // Delay to not show instant locked state before actual connection
            delay(500)

            val currentCount = changeServerPrefs.changeCounter + 1
            changeServerPrefs.changeCounter =
                if (currentCount > restrictConfig.changeServerConfig().maxAttemptCount) 0 else currentCount
            changeServerPrefs.lastChangeTimestamp = System.currentTimeMillis()
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
