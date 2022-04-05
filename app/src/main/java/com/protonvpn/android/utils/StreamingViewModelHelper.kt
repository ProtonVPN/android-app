/*
 * Copyright (c) 2021 Proton Technologies AG
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

package com.protonvpn.android.utils

import android.net.Uri
import com.protonvpn.android.appconfig.AppConfig

interface StreamingViewModelHelper {

    val serverManager: ServerManager
    val appConfig: AppConfig

    val displayStreamingIcons get() = appConfig.getFeatureFlags().streamingServicesLogos

    data class StreamingService(val name: String, val iconUrl: String?)
    fun streamingServices(country: String): List<StreamingService> =
        serverManager.streamingServicesModel?.let { streamingServices ->
            streamingServices.getForAllTiers(country).map { streamingService ->
                StreamingService(
                    streamingService.name,
                    if (displayStreamingIcons) {
                        Uri.parse(streamingServices.resourceBaseURL)
                            .buildUpon()
                            .appendPath(streamingService.iconName)
                            .toString()
                    } else {
                        null
                    }
                )
            }
        } ?: emptyList()
}
