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
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import dagger.Reusable
import kotlinx.coroutines.flow.first
import me.proton.core.network.domain.ApiResult
import javax.inject.Inject

@Reusable
class UpdateLoadsFromApi @Inject constructor(
    private val api: ProtonApiRetroFit,
    private val serversDataManager: ServersDataManager,
) {
    suspend operator fun invoke(): PeriodicActionResult<out Any> {
        val serversData = serversDataManager.serverLists.first()
        val statusId = serversData.statusId
        if (statusId == null) {
            // This may happen when only bundled guest hole servers are added to server manager.
            ProtonLogger.logCustom(LogCategory.APP, "Update loads with no statusId")
            return PeriodicActionResult(Unit, isSuccess = true)
        }

        val result = api.getBinaryStatus(statusId)
        if (result is ApiResult.Success) {
            serversDataManager.updateBinaryLoads(statusId, result.value)
        }
        return result.toPeriodicActionResult()
    }
}
