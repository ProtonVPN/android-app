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

import com.protonvpn.android.appconfig.usecase.LargeMetricsSampler
import com.protonvpn.android.observability.VpnErrorsTotal
import com.protonvpn.android.observability.VpnFallbacksTotal
import com.protonvpn.android.observability.YesNoUnknown
import com.protonvpn.android.observability.toObservability
import com.protonvpn.android.redesign.vpn.ConnectIntent
import dagger.Reusable
import me.proton.core.observability.domain.ObservabilityManager
import javax.inject.Inject

@Reusable
class VpnErrorAndFallbackObservability @Inject constructor(
    private val observabilityManager: ObservabilityManager,
    private val largeMetricsSampler: LargeMetricsSampler,
) {

    fun reportError(error: ErrorType) {
        largeMetricsSampler { multiplier ->
            val vpnError = VpnErrorsTotal(error.toObservabiliy(), multiplier)
            observabilityManager.enqueue(vpnError)
        }
    }

    fun reportFallback(fallback: VpnFallbackResult) {
        val vpnFallback = when(fallback) {
            is VpnFallbackResult.Error -> {
                // Fallbacks should happen only for UI connect intents, though it's possible we have some edge cases.
                val uiConnectIntent = fallback.originalParams.connectIntent as? ConnectIntent
                VpnFallbacksTotal(
                    VpnFallbacksTotal.FallbackType.Error,
                    switchReason = fallback.reason?.toObservability() ?: VpnFallbacksTotal.SwitchReason.Error,
                    switchedToSameServer = YesNoUnknown.Unknown,
                    isProfile = uiConnectIntent?.let { it.profileId != null }.toObservability(),
                    originalConnectIntentType = uiConnectIntent?.toObservability() ?: VpnFallbacksTotal.ConnectIntentType.None
                )
            }

            is VpnFallbackResult.Switch.SwitchServer ->
                VpnFallbacksTotal(
                    VpnFallbacksTotal.FallbackType.ServerSwitch,
                    switchReason = fallback.reason.toObservability(),
                    switchedToSameServer = (fallback.fromServer == fallback.toServer).toObservability(),
                    originalConnectIntentType = fallback.connectIntent.toObservability(),
                    isProfile = (fallback.connectIntent.profileId != null).toObservability(),
                )

            is VpnFallbackResult.Switch.SwitchConnectIntent ->
                VpnFallbacksTotal(
                    VpnFallbacksTotal.FallbackType.ConnectIntentSwitch,
                    switchReason = fallback.reason?.toObservability() ?: VpnFallbacksTotal.SwitchReason.Error,
                    switchedToSameServer = (fallback.fromServer == fallback.toServer).toObservability(),
                    originalConnectIntentType = fallback.fromConnectIntent.toObservability(),
                    isProfile = (fallback.fromConnectIntent.profileId != null).toObservability(),
                )
        }
        observabilityManager.enqueue(vpnFallback)
    }

    fun reportFallbackFailure(connectIntent: ConnectIntent, reason: SwitchServerReason) {
        val vpnFallback = VpnFallbacksTotal(
            VpnFallbacksTotal.FallbackType.Error,
            reason.toObservability(),
            switchedToSameServer = YesNoUnknown.Unknown,
            originalConnectIntentType = connectIntent.toObservability(),
            isProfile = (connectIntent.profileId != null).toObservability(),
        )
        observabilityManager.enqueue(vpnFallback)
    }

    private fun ErrorType.toObservabiliy(): VpnErrorsTotal.VpnErrorType = when (this) {
        ErrorType.AUTH_FAILED_INTERNAL -> VpnErrorsTotal.VpnErrorType.ErrorAuthFailedInternal
        ErrorType.AUTH_FAILED -> VpnErrorsTotal.VpnErrorType.ErrorAuthFailed
        ErrorType.PEER_AUTH_FAILED -> VpnErrorsTotal.VpnErrorType.ErrorPeerAuthFailed
        ErrorType.NO_PROFILE_FALLBACK_AVAILABLE -> VpnErrorsTotal.VpnErrorType.ErrorProfileFallbackUnavailable
        ErrorType.UNREACHABLE -> VpnErrorsTotal.VpnErrorType.ErrorUnreachable
        ErrorType.UNREACHABLE_INTERNAL -> VpnErrorsTotal.VpnErrorType.ErrorUnreachableInternal
        ErrorType.MAX_SESSIONS -> VpnErrorsTotal.VpnErrorType.ErrorMaxSessions
        ErrorType.GENERIC_ERROR -> VpnErrorsTotal.VpnErrorType.ErrorGeneric
        ErrorType.MULTI_USER_PERMISSION -> VpnErrorsTotal.VpnErrorType.ErrorMultiUserPermission
        ErrorType.LOCAL_AGENT_ERROR -> VpnErrorsTotal.VpnErrorType.ErrorLocalAgent
        ErrorType.SERVER_ERROR -> VpnErrorsTotal.VpnErrorType.ErrorServerError
        ErrorType.POLICY_VIOLATION_DELINQUENT -> VpnErrorsTotal.VpnErrorType.ErrorPolicyDelinquent
        ErrorType.POLICY_VIOLATION_LOW_PLAN -> VpnErrorsTotal.VpnErrorType.ErrorPolicyLowPlan
        ErrorType.POLICY_VIOLATION_BAD_BEHAVIOUR -> VpnErrorsTotal.VpnErrorType.ErrorPolicyBadBehavior
        ErrorType.TORRENT_NOT_ALLOWED -> VpnErrorsTotal.VpnErrorType.ErrorPolicyTorrent
        ErrorType.KEY_USED_MULTIPLE_TIMES -> VpnErrorsTotal.VpnErrorType.ErrorKeyUsedMultipleTimes
    }

    private fun ConnectIntent.toObservability(): VpnFallbacksTotal.ConnectIntentType = when (this) {
        is ConnectIntent.FastestInCountry -> when {
            country.isFastest -> VpnFallbacksTotal.ConnectIntentType.FastestCountry
            country.isFastestExcludingMyCountry -> VpnFallbacksTotal.ConnectIntentType.FastestCountryExcludingMine
            else -> VpnFallbacksTotal.ConnectIntentType.FastestInCountry
        }
        is ConnectIntent.FastestInCity -> VpnFallbacksTotal.ConnectIntentType.FastestInCity
        is ConnectIntent.FastestInState -> VpnFallbacksTotal.ConnectIntentType.FastestInState
        is ConnectIntent.Gateway -> VpnFallbacksTotal.ConnectIntentType.Gateway
        is ConnectIntent.SecureCore -> VpnFallbacksTotal.ConnectIntentType.SecureCore
        is ConnectIntent.Server -> VpnFallbacksTotal.ConnectIntentType.SpecificServer
    }

    private fun SwitchServerReason.toObservability(): VpnFallbacksTotal.SwitchReason = when (this) {
        is SwitchServerReason.Downgrade -> VpnFallbacksTotal.SwitchReason.Downgrade
        SwitchServerReason.ServerInMaintenance -> VpnFallbacksTotal.SwitchReason.ServerInMaintenance
        SwitchServerReason.ServerUnavailable -> VpnFallbacksTotal.SwitchReason.ServerUnavailable
        SwitchServerReason.ServerUnreachable -> VpnFallbacksTotal.SwitchReason.ServerUnreachable
        SwitchServerReason.UnknownAuthFailure -> VpnFallbacksTotal.SwitchReason.UnknownAuthFailure
        SwitchServerReason.UserBecameDelinquent -> VpnFallbacksTotal.SwitchReason.Delinquent
    }
}
