/*
 * Copyright (c) 2017 Proton Technologies AG
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
package com.protonvpn.android.bus

import com.protonvpn.android.utils.ConnectionTools

class TrafficUpdate(
    val timestampMs: Long,
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val sessionDownload: Long,
    val sessionUpload: Long,
    val sessionTimeSeconds: Int
) {
    val notificationString: String
        get() = "↓ $sessionDownloadString | $downloadSpeedString  ↑ $sessionUploadString | $uploadSpeedString"

    val downloadSpeedString: String
        get() = ConnectionTools.bytesToSize(downloadSpeed) + "/s"

    val uploadSpeedString: String
        get() = ConnectionTools.bytesToSize(uploadSpeed) + "/s"

    private val sessionDownloadString: String
        get() = ConnectionTools.bytesToSize(sessionDownload)

    private val sessionUploadString: String
        get() = ConnectionTools.bytesToSize(sessionUpload)
}
