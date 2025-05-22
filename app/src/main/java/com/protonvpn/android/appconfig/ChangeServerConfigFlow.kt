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

package com.protonvpn.android.appconfig

import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class ChangeServerConfig(
    val shortDelayInSeconds: Int,
    val maxAttemptCount: Int,
    val longDelayInSeconds: Int
)

interface ChangeServerConfigFlow : Flow<ChangeServerConfig>

@Reusable
class DefaultChangeServerConfigFlow @Inject constructor(
    appConfig: AppConfig,
): ChangeServerConfigFlow {

    private val flow =  appConfig.appConfigFlow.map { appConfigResponse ->
        ChangeServerConfig(
            shortDelayInSeconds = appConfigResponse.changeServerShortDelayInSeconds,
            maxAttemptCount = appConfigResponse.changeServerAttemptLimit,
            longDelayInSeconds = appConfigResponse.changeServerLongDelayInSeconds
        )
    }.distinctUntilChanged()

    override suspend fun collect(collector: FlowCollector<ChangeServerConfig>) {
        flow.collect(collector)
    }


}
