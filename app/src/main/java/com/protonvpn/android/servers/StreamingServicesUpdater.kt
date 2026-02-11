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

package com.protonvpn.android.servers

import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.periodicupdates.IsInForeground
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateSpec
import com.protonvpn.android.appconfig.periodicupdates.registerApiCall
import com.protonvpn.android.servers.api.StreamingServicesResponse
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import me.proton.core.network.domain.ApiResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private val STREAMING_SERVICES_CALL_DELAY = TimeUnit.DAYS.toMillis(2)

@Reusable
class StreamingServicesUpdater @Inject constructor(
    private val api: dagger.Lazy<ProtonApiRetroFit>,
    private val streamingServicesStore: dagger.Lazy<StreamingServicesObjectStore>,
    private val periodicUpdateManager: PeriodicUpdateManager,
    @IsInForeground private val inForeground: Flow<Boolean>,
) {
    private val streamingServicesUpdate = periodicUpdateManager.registerApiCall(
        "streaming_services",
        ::update,
        PeriodicUpdateSpec(STREAMING_SERVICES_CALL_DELAY, setOf(inForeground))
    )

    suspend fun forceUpdate() {
        periodicUpdateManager.executeNow(streamingServicesUpdate)
    }

    private suspend fun update(): ApiResult<StreamingServicesResponse> =
        api.get().getStreamingServices().apply {
            valueOrNull?.let { streamingServicesStore.get().store(it) }
        }
}