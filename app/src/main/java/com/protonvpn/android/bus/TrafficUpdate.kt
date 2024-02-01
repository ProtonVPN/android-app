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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.R
import com.protonvpn.android.utils.ConnectionTools
import java.text.DecimalFormat

class TrafficUpdate(
    val monotonicTimestampMs: Long,
    val sessionStartTimestampMs: Long,
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

    @Composable
    fun speedToString(sizeInBytes: Long): String {
        val b = sizeInBytes.toDouble()
        val kb = sizeInBytes / 1000.0
        val mb = ((sizeInBytes / 1000.0) / 1000.0)
        val gb = (((sizeInBytes / 1000.0) / 1000.0) / 1000.0)
        val tb = ((((sizeInBytes / 1000.0) / 1000.0) / 1000.0) / 1000.0)

        val dec = remember { DecimalFormat("0.00") }

        return when {
            tb > 1 -> stringResource(R.string.speed_tb_per_second, dec.format(tb))
            gb > 1 -> stringResource(R.string.speed_gb_per_second, dec.format(gb))
            mb > 1 -> stringResource(R.string.speed_mb_per_second, dec.format(mb))
            kb > 1 -> stringResource(R.string.speed_kb_per_second, dec.format(kb))
            else -> stringResource(R.string.speed_bits_per_second, dec.format(b))
        }
    }

    private val sessionDownloadString: String
        get() = ConnectionTools.bytesToSize(sessionDownload)

    private val sessionUploadString: String
        get() = ConnectionTools.bytesToSize(sessionUpload)
}
