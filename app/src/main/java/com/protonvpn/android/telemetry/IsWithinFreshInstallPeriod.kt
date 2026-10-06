/*
 * Copyright (c) 2026. Proton AG
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

import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.di.WallClock
import dagger.Reusable
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours

private val GRACE_PERIOD = 24.hours

@Reusable
class IsWithinFreshInstallPeriod @Inject constructor(
    private val appFeaturesPrefs: AppFeaturesPrefs,
    @param:WallClock private val wallClock: () -> Long,
) {
    operator fun invoke(): Boolean {
        val installTimestamp = appFeaturesPrefs.installTimestamp
        return installTimestamp > 0 && wallClock() - installTimestamp < GRACE_PERIOD.inWholeMilliseconds
    }
}
