/*
 * Copyright (c) 2024. Proton AG
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

import com.protonvpn.android.appconfig.usecase.LargeMetricsSampler
import com.protonvpn.android.observability.VpnConnectionResultTotal
import com.protonvpn.android.utils.DebugUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.core.observability.domain.ObservabilityManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnConnectionObservability @Inject constructor(
    mainScope: CoroutineScope,
    vpnStateMonitor: VpnStateMonitor,
    private val observabilityManager: ObservabilityManager,
    private val largeMetricSampler: LargeMetricsSampler,
) {
    init {
        vpnStateMonitor.status.onEach { status ->
            val state = status.state
            val resultType = when {
                state is VpnState.Connected -> VpnConnectionResultTotal.ResultType.Success
                state is VpnState.Error && state.isFinal -> state.type.toResultType()
                else -> null
            }
            if (resultType != null) {
                largeMetricSampler { multiplier ->
                    observabilityManager.enqueue(VpnConnectionResultTotal(resultType, multiplier))
                }
            }
        }.launchIn(mainScope)
    }

    private fun ErrorType.toResultType() = when (this) {
        ErrorType.AUTH_FAILED -> VpnConnectionResultTotal.ResultType.ErrorAuthFailed
        ErrorType.PEER_AUTH_FAILED -> VpnConnectionResultTotal.ResultType.ErrorPeerAuthFailed
        ErrorType.UNREACHABLE -> VpnConnectionResultTotal.ResultType.ErrorUnreachable
        ErrorType.MAX_SESSIONS -> VpnConnectionResultTotal.ResultType.ErrorMaxSessions
        ErrorType.GENERIC_ERROR -> VpnConnectionResultTotal.ResultType.ErrorGeneric
        ErrorType.MULTI_USER_PERMISSION -> VpnConnectionResultTotal.ResultType.ErrorMultiUserPermission
        ErrorType.LOCAL_AGENT_ERROR -> VpnConnectionResultTotal.ResultType.ErrorLocalAgent
        ErrorType.SERVER_ERROR -> VpnConnectionResultTotal.ResultType.ErrorServerError
        ErrorType.POLICY_VIOLATION_DELINQUENT -> VpnConnectionResultTotal.ResultType.ErrorPolicyDelinquent
        ErrorType.POLICY_VIOLATION_LOW_PLAN -> VpnConnectionResultTotal.ResultType.ErrorPolicyLowPlan
        ErrorType.POLICY_VIOLATION_BAD_BEHAVIOUR -> VpnConnectionResultTotal.ResultType.ErrorPolicyBadBehavior
        ErrorType.TORRENT_NOT_ALLOWED -> VpnConnectionResultTotal.ResultType.ErrorPolicyTorrent
        ErrorType.KEY_USED_MULTIPLE_TIMES -> VpnConnectionResultTotal.ResultType.ErrorKeyUsedMultipleTimes
        ErrorType.NO_PROFILE_FALLBACK_AVAILABLE -> VpnConnectionResultTotal.ResultType.ErrorProfileFallbackUnavailable
        else -> {
            DebugUtils.fail("Unknown error type, add the missing mapping to VpnConnectionResultTotal.ResultType")
            VpnConnectionResultTotal.ResultType.ErrorUnknown
        }
    }
}
