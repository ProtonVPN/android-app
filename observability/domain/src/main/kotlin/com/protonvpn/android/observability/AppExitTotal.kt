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
@Schema(description = "VPN app exit information")
@SchemaId("https://proton.me/android_vpn_app_exit_total_v1.schema.json")
data class AppExitTotal(
    override val Labels: LabelsData,
    @Required  override val Value: Long
) : VpnObservabilityData() {

    @Serializable
    data class LabelsData(
        val exitReason: ExitReason,
        val appImportance: AppImportance
    )

    constructor(exitReason: ExitReason, appImportance: AppImportance, value: Long) : this(LabelsData(exitReason, appImportance), value)

    enum class ExitReason {
        Anr,
        Crash,
        CrashNative,
        ExcessiveResourceUsage,
        Freezer,
        InitializationFailure,
        LowMemory,
        PackageStateChange,
        PackageUpdated,
        PermissionChange,
        Signal9,
        SignalOther,
        Unknown,
        UserRequest,
        Other,

        Unsupported // Reported for values added in future Android versions.
    }

    enum class AppImportance {
        Cached,
        CantSaveState,
        Foreground,
        ForegroundService,
        Gone,
        Perceptible,
        Service,
        TopSleeping,
        Visible,

        Unsupported // Reported for values added in future Android versions.
    }
}
