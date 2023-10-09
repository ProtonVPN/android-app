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

package com.protonvpn.android.telemetry

import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import dagger.Reusable
import kotlinx.coroutines.flow.first
import me.proton.core.domain.entity.UserId
import me.proton.core.telemetry.domain.usecase.IsTelemetryEnabled
import javax.inject.Inject

@Reusable
class IsVpnTelemetryEnabled @Inject constructor(
    private val effectiveCurrentUserSettings: EffectiveCurrentUserSettings
) : IsTelemetryEnabled {
    override suspend fun invoke(userId: UserId?): Boolean =
        if (userId == null) LocalUserSettings.Default.telemetry
        else effectiveCurrentUserSettings.telemetry.first()
}
