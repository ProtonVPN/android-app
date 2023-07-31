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

package com.protonvpn.android.models.vpn

import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.utils.BytesFileWriter
import com.protonvpn.android.utils.FileObjectStore
import com.protonvpn.android.utils.KotlinCborObjectSerializer
import com.protonvpn.android.utils.ObjectStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File

// Faster, dedicated server list storage. Serialization is the bottleneck and will block main
// thread, once for reading at launch and for each save(). Should be used only from main thread.
class ServersStore(
    private val store: ObjectStore<ServersSerializationData>,
) {
    var allServers: List<Server> = emptyList()

    init {
        val data = runBlocking { store.read() }
        if (data != null) {
            allServers = if (data.allServers.isEmpty() && data.vpnCountries.isNotEmpty()) {
                extractServers(data.vpnCountries, data.secureCoreEntryCountries, data.secureCoreExitCountries)
            } else {
                data.allServers
            }
        }
    }

    fun save() {
        val data = ServersSerializationData(allServers)
        store.store(data)
    }

    fun migrate(
        vpnCountries: List<VpnCountry>,
        secureCoreEntryCountries: List<VpnCountry>,
        secureCoreExitCountries: List<VpnCountry>,
    ) {
        allServers = extractServers(vpnCountries, secureCoreEntryCountries, secureCoreExitCountries)
        save()
    }

    fun clear() {
        allServers = emptyList()
        store.clear()
    }

    private fun extractServers(vararg countryLists: List<VpnCountry>): List<Server> =
        countryLists.asSequence()
            .flatMap { countries -> countries.asSequence().flatMap { country -> country.serverList } }
            .toList()

    companion object {
        const val STORE_FILENAME = "servers_data"

        fun create(
            mainScope: CoroutineScope,
            dispatcherProvider: VpnDispatcherProvider,
            file: File,
        ) = ServersStore(
            FileObjectStore(
                file,
                mainScope,
                dispatcherProvider,
                KotlinCborObjectSerializer(ServersSerializationData.serializer()),
                BytesFileWriter(),
            )
        )
    }
}

// This class is serialized to file, take that into account when changing it.
@kotlinx.serialization.Serializable
class ServersSerializationData(
    val allServers: List<Server> = emptyList(),

    // Deprecated, used only for migration.
    val vpnCountries: List<VpnCountry> = emptyList(),
    val secureCoreEntryCountries: List<VpnCountry> = emptyList(),
    val secureCoreExitCountries: List<VpnCountry> = emptyList(),
)
