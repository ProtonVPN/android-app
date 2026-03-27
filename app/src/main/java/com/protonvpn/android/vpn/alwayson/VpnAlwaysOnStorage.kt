/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.android.vpn.alwayson

import com.protonvpn.android.userstorage.JsonDataStoreSerializer
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@Reusable
class VpnAlwaysOnStorage @Inject constructor(
    mainScope: CoroutineScope,
    private val localDataStoreFactory: LocalDataStoreFactory,
) {

    private val deferredDataStore = mainScope.async {
        localDataStoreFactory.getDataStore(
            fileName = FILE_NAME,
            serializer = JsonDataStoreSerializer(
                defaultValue = VpnAlwaysOn(
                    isEnabled = false,
                    isLockdownEnabled = false,
                    isDefaultValue = true,
                ),
                serializer = VpnAlwaysOn.serializer(),
            ),
            migrations = emptyList(),
        )
    }

    suspend fun getLastKnownVpnAlwaysOn(): VpnAlwaysOn? = getDataStore()
        .data
        .first()
        .takeUnless(VpnAlwaysOn::isDefaultValue)

    suspend fun setVpnAlwaysOn(vpnAlwaysOn: VpnAlwaysOn) {
        getDataStore().updateData { vpnAlwaysOn }
    }

    private suspend fun getDataStore() = deferredDataStore.await()

    private companion object {

        private const val FILE_NAME = "vpn_always_on_data_store"

    }

}
