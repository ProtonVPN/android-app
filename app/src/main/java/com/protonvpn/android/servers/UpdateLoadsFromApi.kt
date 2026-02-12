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
import com.protonvpn.android.appconfig.periodicupdates.PeriodicActionResult
import com.protonvpn.android.appconfig.periodicupdates.toPeriodicActionResult
import com.protonvpn.android.ui.home.GetNetZone
import dagger.Reusable
import kotlinx.coroutines.flow.first
import me.proton.core.network.domain.ApiResult
import javax.inject.Inject

@Reusable
class UpdateLoadsFromApi @Inject constructor(
    private val api: ProtonApiRetroFit,
    private val serversDataManager: ServersDataManager,
    private val binaryServerStatusEnabled: IsBinaryServerStatusEnabled,
    private val getNetZone: GetNetZone,
) {
    suspend operator fun invoke(): PeriodicActionResult<out Any> {
        val serversData = serversDataManager.serverLists.first()
        if (serversData.allServers.isEmpty()) {
            return PeriodicActionResult(Unit, isSuccess = true)
        }

        val statusId = serversData.statusId
        return if (binaryServerStatusEnabled() && statusId != null) {
            val result = api.getBinaryStatus(statusId)
            if (result is ApiResult.Success) {
                serversDataManager.updateBinaryLoads(statusId, result.value)
            }
            result
        } else {
            val result = api.getLoads(getNetZone())
            if (result is ApiResult.Success) {
                serversDataManager.updateLoads(result.value.loadsList)
            }
            result
        }.toPeriodicActionResult()
    }
}
