/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.appconfig.usecase

import com.protonvpn.android.appconfig.AppConfig
import dagger.Reusable
import javax.inject.Inject
import kotlin.random.Random

@Reusable
class LargeMetricsSampler @Inject constructor(
    private val appConfig: dagger.Lazy<AppConfig>,
    private val random: Random,
) {
    operator fun invoke(block: (Long) -> Unit) {
        val multiplier = appConfig.get().appConfigFlow.value.largeMetricsSamplingMultiplier.toLong()
        val randomValue = random.nextLong(multiplier)
        if (randomValue == 0L)
            block(multiplier)
    }
}