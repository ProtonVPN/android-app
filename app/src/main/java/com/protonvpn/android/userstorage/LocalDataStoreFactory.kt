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

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

interface LocalDataStoreFactory {
    suspend fun <T> getDataStore(
        fileName: String,
        serializer: Serializer<T>,
        migrations: List<DataMigration<T>>
    ): DataStore<T>
}

@Singleton
class DefaultLocalDataStoreFactory @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalDataStoreFactory {
    // Single-file DataStores can be created one per process. Store them in the map in case they are requested again.
    private val dataStores: MutableMap<String, DataStore<*>> = HashMap()
    private val mutex = Mutex()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> getDataStore(
        fileName: String,
        serializer: Serializer<T>,
        migrations: List<DataMigration<T>>
    ): DataStore<T> = mutex.withLock {
        dataStores.getOrElse(fileName) {
            DataStoreFactory.create(
                serializer,
                migrations = migrations,
                produceFile = { context.dataStoreFile(fileName) }
            )
                .also { newDataStore ->
                    dataStores[fileName] = newDataStore
                }
        } as DataStore<T>
    }
}
