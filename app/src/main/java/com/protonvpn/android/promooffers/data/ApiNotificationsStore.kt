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

package com.protonvpn.android.promooffers.data

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import com.protonvpn.android.userstorage.SharedStoreProvider
import com.protonvpn.android.userstorage.StoreProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiNotificationsStoreProvider @Inject constructor(
    factory: LocalDataStoreFactory,
) : StoreProvider<ApiNotificationsResponse>(
    filename = "api_notifications",
    default = ApiNotificationsResponse(emptyList()),
    serializer = ApiNotificationsResponse.serializer(),
    factory = factory,
    corruptionHandler = ReplaceFileCorruptionHandler { e ->
        ProtonLogger.logCustom(LogLevel.WARN, LogCategory.PROMO, "Corrupted cache ($e: ${e.cause}), clearing.")
        ApiNotificationsResponse(emptyList())
    }
)

@Singleton
class ApiNotificationsStore @Inject constructor(
    apiNotificationsStoreProvider: ApiNotificationsStoreProvider,
) {
    private val dataStore = SharedStoreProvider(apiNotificationsStoreProvider)

    fun observe(): Flow<List<ApiNotification>> = dataStore.sharedDataFlow().map { it.notifications }

    suspend fun update(transform: (List<ApiNotification>) -> List<ApiNotification>) {
        dataStore.sharedUpdate { ApiNotificationsResponse(transform(it.notifications)) }
    }

    suspend fun save(newNotifications: ApiNotificationsResponse) {
        dataStore.sharedUpdate { newNotifications }
    }
}
