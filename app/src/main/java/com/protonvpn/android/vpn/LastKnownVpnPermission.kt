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

package com.protonvpn.android.vpn

import com.protonvpn.android.userstorage.JsonDataStoreSerializer
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches the result of the last VpnService.prepare() call.
 *
 * VpnService.prepare() has a side effect: if the app has VPN permission it becomes the active VPN app, stopping any
 * other VPN. Use this value where the permission state is needed. The value may be stale (e.g. the user revoked
 * the permission in system settings), so callers need to handle the permission missing at connection time.
 */
@Singleton
class LastKnownVpnPermission @Inject constructor(
    private val mainScope: CoroutineScope,
    private val localDataStoreFactory: LocalDataStoreFactory,
) {

    private val deferredDataStore = mainScope.async {
        localDataStoreFactory.getDataStore(
            fileName = FILE_NAME,
            serializer = JsonDataStoreSerializer(
                defaultValue = LastKnownVpnPermissionData(hasPermission = null),
                serializer = LastKnownVpnPermissionData.serializer(),
            ),
            migrations = emptyList(),
        )
    }

    val hasPermission: Flow<Boolean> = flow { emitAll(getDataStore().data) }
        .map { it.hasPermission ?: false }
        .distinctUntilChanged()

    fun update(hasPermission: Boolean) {
        mainScope.launch {
            getDataStore().updateData { it.copy(hasPermission = hasPermission) }
        }
    }

    // For migrating existing users: don't override a value obtained from VpnService.prepare().
    suspend fun assumeGrantedIfUnknown() {
        getDataStore().updateData { data ->
            if (data.hasPermission == null) data.copy(hasPermission = true) else data
        }
    }

    private suspend fun getDataStore() = deferredDataStore.await()

    private companion object {
        private const val FILE_NAME = "last_known_vpn_permission_data_store"
    }
}

@Serializable
data class LastKnownVpnPermissionData(
    // null until the first VpnService.prepare() call.
    val hasPermission: Boolean?
)
