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

package com.protonvpn.android.servers

import android.content.Context
import android.net.Uri
import com.protonvpn.android.appconfig.GetFeatureFlags
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.servers.api.StreamingServicesResponse
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.utils.BytesFileWriter
import com.protonvpn.android.utils.FileObjectStore
import com.protonvpn.android.utils.KotlinCborObjectSerializer
import com.protonvpn.android.utils.ObjectStore
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.StreamingServicesModel
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamingServicesObjectStore(
    private val store: ObjectStore<StreamingServicesResponse>,
) : ObjectStore<StreamingServicesResponse> by store {

    @Inject
    constructor(
        mainScope: CoroutineScope,
        @ApplicationContext context: Context,
        dispatcherProvider: VpnDispatcherProvider
    ) : this(
        FileObjectStore(
            File(context.filesDir, "streaming_services_data"),
            mainScope,
            dispatcherProvider,
            KotlinCborObjectSerializer(StreamingServicesResponse.serializer()),
            BytesFileWriter(),
        )
    )
}

data class StreamingService(val name: String, val iconUrl: String?)

@Reusable
class GetStreamingServices @Inject constructor(
    private val store: StreamingServicesObjectStore,
    isTvCheck: IsTvCheck
) {
    private val isTV = isTvCheck.invoke()
    suspend operator fun invoke(countryCode: String): List<StreamingService> {
        val data = store.read() ?: return emptyList()

        val streamingServices = StreamingServicesModel(data)
        return streamingServices.getForAllTiers(countryCode).map { streamingService ->
            val iconName =
                if (isTV) streamingService.iconName else streamingService.coloredIconName
            StreamingService(
                streamingService.name,
                Uri.parse(streamingServices.resourceBaseURL)
                    .buildUpon()
                    .appendEncodedPath(iconName)
                    .toString()
            )
        }
    }
}
