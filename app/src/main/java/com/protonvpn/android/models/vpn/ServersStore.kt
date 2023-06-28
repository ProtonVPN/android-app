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
    var vpnCountries: List<VpnCountry>
    var secureCoreEntryCountries: List<VpnCountry>
    var secureCoreExitCountries: List<VpnCountry>

    init {
        val data = runBlocking { store.read() }
        vpnCountries = data?.vpnCountries ?: emptyList()
        secureCoreEntryCountries = data?.secureCoreEntryCountries ?: emptyList()
        secureCoreExitCountries = data?.secureCoreExitCountries ?: emptyList()
    }

    fun save() {
        store.store(ServersSerializationData(vpnCountries, secureCoreEntryCountries, secureCoreExitCountries))
    }

    fun migrate(
        vpnCountries: List<VpnCountry>,
        secureCoreEntryCountries: List<VpnCountry>,
        secureCoreExitCountries: List<VpnCountry>,
    ) {
        this.vpnCountries = vpnCountries
        this.secureCoreEntryCountries = secureCoreEntryCountries
        this.secureCoreExitCountries = secureCoreExitCountries
        save()
    }

    fun clear() {
        vpnCountries = emptyList()
        secureCoreEntryCountries = emptyList()
        secureCoreExitCountries = emptyList()
        store.clear()
    }

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
    val vpnCountries: List<VpnCountry>,
    val secureCoreEntryCountries: List<VpnCountry>,
    val secureCoreExitCountries: List<VpnCountry>,
)
