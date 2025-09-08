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

package com.protonvpn.android.redesign.recents.usecases

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.recents.data.ConnectionType
import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.recents.data.DefaultConnectionDao
import com.protonvpn.android.redesign.recents.data.DefaultConnectionEntity
import dagger.Reusable
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@Reusable
class SetDefaultConnection @Inject constructor(
    private val currentUser: CurrentUser,
    private val defaultConnectionDao: DefaultConnectionDao,
) {

    suspend operator fun invoke(newDefaultConnection: DefaultConnection) {
        currentUser.vpnUser()?.let { vpnUser ->
            val defaultConnectionEntity = when (newDefaultConnection) {
                DefaultConnection.FastestConnection -> DefaultConnectionEntity(
                    userId = vpnUser.userId.id,
                    recentId = null,
                    connectionType = ConnectionType.FASTEST,
                )

                DefaultConnection.LastConnection -> DefaultConnectionEntity(
                    userId = vpnUser.userId.id,
                    recentId = null,
                    connectionType = ConnectionType.LAST_CONNECTION,
                )

                is DefaultConnection.Recent -> DefaultConnectionEntity(
                    userId = vpnUser.userId.id,
                    recentId = newDefaultConnection.recentId,
                    connectionType = ConnectionType.RECENT,
                )
            }

            defaultConnectionDao.insert(defaultConnectionEntity)
        }
    }

}
