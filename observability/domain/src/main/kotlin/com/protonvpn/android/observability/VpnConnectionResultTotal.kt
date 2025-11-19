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

package com.protonvpn.android.observability

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import me.proton.core.observability.domain.entity.SchemaId

@Serializable
@Schema(description = "VPN connection (success or failure) event")
@SchemaId("https://proton.me/android_vpn_connection_result_total_v1.schema.json")
data class VpnConnectionResultTotal(
    override val Labels: LabelsData,
    @Required override val Value: Long
) : VpnObservabilityData() {
    constructor(result: ResultType, value: Long) : this(LabelsData(result), value)

    @Serializable
    data class LabelsData(
        val connectResult: ResultType
    )

    enum class ResultType {
        Success,
        ErrorAuthFailed,
        ErrorPeerAuthFailed,
        ErrorUnreachable,
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
        ErrorProfileFallbackUnavailable,
        ErrorUnknown,
    }
}
