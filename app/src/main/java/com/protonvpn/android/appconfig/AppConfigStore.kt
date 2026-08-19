/*
 * Copyright (c) 2026. Proton AG
 *
 *  This file is part of ProtonVPN.
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

import androidx.datastore.core.DataMigration
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import com.protonvpn.android.userstorage.SharedStoreProvider
import com.protonvpn.android.userstorage.StoreProvider
import com.protonvpn.android.utils.Storage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private class AppConfigStorageMigration : DataMigration<AppConfigResponse> {
    override suspend fun shouldMigrate(currentData: AppConfigResponse): Boolean =
        Storage.containsKey(AppConfigResponse::class.java)

    override suspend fun migrate(currentData: AppConfigResponse): AppConfigResponse {
        val legacy =
            Storage.load(AppConfigResponse::class.java, AppConfigResponseLegacyStorage.serializer())
        // The value should be present, otherwise shouldMigrate returns false.
        return legacy?.migrate() ?: AppConfigResponse()
    }

    override suspend fun cleanUp() {
        Storage.delete(AppConfigResponse::class.java)
    }

}

@Singleton
class AppConfigStoreProvider @Inject constructor(
    factory: LocalDataStoreFactory
) : StoreProvider<AppConfigResponse>(
    filename = "app_config",
    default = AppConfigResponse(),
    serializer = AppConfigResponse.serializer(),
    factory = factory,
    migrations = listOf(AppConfigStorageMigration())
)

@Singleton
class AppConfigStore @Inject constructor(
    appConfigStoreProvider: AppConfigStoreProvider,
) {
    private val dataStore = SharedStoreProvider(appConfigStoreProvider)

    fun observe(): Flow<AppConfigResponse> = dataStore.sharedDataFlow()

    suspend fun save(newConfig: AppConfigResponse) {
        dataStore.sharedUpdate { newConfig }
    }
}
