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

package com.protonvpn.test.shared

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

// Can't use mockk to implement this because it's used in UI tests on older Android versions.
@Singleton
class InMemoryDataStoreFactory @Inject constructor() : LocalDataStoreFactory {
    private val allStores = mutableMapOf<String, DataStore<*>>()
    private val mutex = Mutex()

    override suspend fun <T> getDataStore(
        fileName: String,
        serializer: Serializer<T>,
        migrations: List<DataMigration<T>>
    ): DataStore<T> = mutex.withLock {
        allStores.getOrElse(fileName) {
            InMemoryDataStore(serializer.defaultValue).also { newStore -> allStores[fileName] = newStore }
        } as DataStore<T>
    }

    override suspend fun <T> getMultiProcessDataStore(
        fileName: String,
        serializer: Serializer<T>,
        migrations: List<DataMigration<T>>
    ): DataStore<T> = getDataStore(fileName, serializer, migrations)
}

class InMemoryDataStore<T>(defaultValue: T) : DataStore<T> {

    private val mutableData = MutableStateFlow(defaultValue)

    override val data: Flow<T> = mutableData

    override suspend fun updateData(transform: suspend (t: T) -> T): T {
        return transform(mutableData.value).also { newData ->
            mutableData.value = newData
        }
    }
}
