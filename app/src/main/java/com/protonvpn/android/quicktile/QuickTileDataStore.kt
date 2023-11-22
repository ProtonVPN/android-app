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
package com.protonvpn.android.quicktile

import androidx.datastore.core.DataStore
import com.protonvpn.android.userstorage.JsonDataStoreSerializer
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import com.protonvpn.android.vpn.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickTileDataStore @Inject constructor(
    mainScope: CoroutineScope,
    private val localDataStoreFactory: LocalDataStoreFactory
) {
    enum class TileState {
        Disabled,
        Connecting,
        WaitingForNetwork,
        Error,
        Connected,
        Disconnecting
    }

    @Serializable
    data class Data(
        val state: TileState,
        val isLoggedIn: Boolean,
        val serverName: String? = null
    )

    private val dataStore: Deferred<DataStore<Data>> = mainScope.async {
        localDataStoreFactory.getMultiProcessDataStore(
            "quick_tile",
            JsonDataStoreSerializer(Data(TileState.Disabled, true), Data.serializer()),
            emptyList()
        )
    }

    suspend fun getDataFlow() = dataStore.await().data
    suspend fun isLoggedIn() = dataStore.await().data.first().isLoggedIn

    suspend fun store(data: Data) {
        dataStore.await().updateData { data }
    }
}

fun VpnState.toTileState() = when (this) {
    VpnState.CheckingAvailability,
    VpnState.Reconnecting,
    VpnState.ScanningPorts,
    VpnState.WaitingForNetwork,
    VpnState.Connecting ->
        QuickTileDataStore.TileState.Connecting
    VpnState.Disabled -> QuickTileDataStore.TileState.Disabled
    VpnState.Connected -> QuickTileDataStore.TileState.Connected
    VpnState.Disconnecting -> QuickTileDataStore.TileState.Disconnecting
    is VpnState.Error -> QuickTileDataStore.TileState.Error
}