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

package com.protonvpn.android.observability

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import me.proton.core.observability.domain.entity.SchemaId

@Serializable
@Schema(description = "VPN error states. Some errors can be recovered e.g. by connecting to a different server.")
@SchemaId("https://proton.me/android_vpn_vpn_errors_total_v1.schema.json")
data class VpnErrorsTotal(
    override val Labels: LabelsData,
    @Required  override val Value: Long
) : VpnObservabilityData() {

    constructor(errorType: VpnErrorType, value: Long) : this(LabelsData(errorType), value)

    @Serializable
    data class LabelsData(
        val errorType: VpnErrorType,
    )

    // The names match VpnConnectionResultTotal.ResultType in case we want to match them.
    enum class VpnErrorType {
        ErrorAuthFailedInternal,
        ErrorAuthFailed,
        ErrorPeerAuthFailed,
        ErrorUnreachable,
        ErrorUnreachableInternal,
        ErrorMaxSessions,
        ErrorGeneric,
        ErrorMultiUserPermission,
        ErrorLocalAgent,
        ErrorServerError,
        ErrorPolicyDelinquent,
        ErrorPolicyLowPlan,
        ErrorPolicyBadBehavior,
        ErrorPolicyTorrent,
        ErrorKeyUsedMultipleTimes,
        ErrorProfileFallbackUnavailable;
    }
}
