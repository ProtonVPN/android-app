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

package com.protonvpn.android.userstorage

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import me.proton.core.domain.entity.UserId

open class StoreProvider<T>(
    private val filename: String,
    default: T,
    serializer: KSerializer<T>,
    private val factory: LocalDataStoreFactory,
    private val migrations: List<DataMigration<T>> = emptyList()
) {
    private val dataStoreSerializer = JsonDataStoreSerializer(default, serializer)

    suspend fun dataStoreWithSuffix(id: String) : DataStore<T> =
        factory.getDataStore(
            listOf(filename, id).joinToString("-"),
            dataStoreSerializer,
            migrations
        )
}

/**
 * Exposes a DataStore for the currently logged in user.
 *
 * When a new user logs in their data store is emitted (along will all updates).
 * When no user is logged in `null` is emitted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CurrentUserStoreProvider<T>(
    private val storeProvider: StoreProvider<T>,
    currentUser: CurrentUser,
) {
    val data: Flow<DataStore<T>?> = currentUser.vpnUserFlow
        .map { vpnUser -> vpnUser?.userId?.toDataStoreSuffix() }
        .distinctUntilChanged()
        .map { suffix -> suffix?.let { storeProvider.dataStoreWithSuffix(suffix) } }

    suspend fun getDataStoreForUser(vpnUser: VpnUser): DataStore<T> =
        storeProvider.dataStoreWithSuffix(vpnUser.userId.toDataStoreSuffix())

    fun dataFlowOrDefaultIfNoUser(default: T): Flow<T> =
        data.flatMapLatest { dataStore -> dataStore?.data ?: flowOf(default) }

    // Note: the update starts the data flow which means it always operates on up-to-date VpnUser.
    suspend fun updateForCurrentUser(transform: (current: T) -> T): T? =
        data.first()?.updateData(transform)

    private fun UserId.toDataStoreSuffix() = id // It's url-friendly Base64 therefore safe for filenames.
}
