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

import android.net.Uri
import com.protonvpn.android.appconfig.GetFeatureFlags
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.utils.ServerManager
import dagger.Reusable
import javax.inject.Inject

data class StreamingService(val name: String, val iconUrl: String?)

@Reusable
class GetStreamingServices @Inject constructor(
    private val serverManager: ServerManager,
    private val featureFlags: GetFeatureFlags,
    isTvCheck: IsTvCheck
) {
    private val displayStreamingIcons get() = featureFlags.value.streamingServicesLogos
    private val isTV = isTvCheck.invoke()
    operator fun invoke(countryCode: String): List<StreamingService> =
        serverManager.streamingServicesModel?.let { streamingServices ->
            streamingServices.getForAllTiers(countryCode).map { streamingService ->
                val iconName = if (isTV) streamingService.iconName else streamingService.coloredIconName
                StreamingService(
                    streamingService.name,
                    if (displayStreamingIcons) {
                        Uri.parse(streamingServices.resourceBaseURL)
                            .buildUpon()
                            .appendEncodedPath(iconName)
                            .toString()

                    } else {
                        null
                    }
                )
            }
        } ?: emptyList()
}
