/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.base.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.protonvpn.android.R
import java.text.DecimalFormat

fun Long.speedBytesToString(context: Context) = SpeedAndSizeFormat.speedOrVolumeString(this, context, speed = true)

fun Long.volumeBytesToString(context: Context) =
    SpeedAndSizeFormat.speedOrVolumeString(this, context, speed = false)

@Composable
fun Long.speedBytesToString() = speedBytesToString(LocalContext.current)

@Composable
fun Long.volumeBytesToString() = volumeBytesToString(LocalContext.current)

private object SpeedAndSizeFormat {
    // Formatting may be used in Compose UI, so make the formatter thread-safe and fast to obtain.
    private val formatter = object : ThreadLocal<DecimalFormat>() {
        override fun initialValue(): DecimalFormat = DecimalFormat("0.00")
    }

    fun speedOrVolumeString(bytes: Long, context: Context, speed: Boolean): String {
        val b = bytes.toDouble()
        val kb = bytes / 1000.0
        val mb = ((bytes / 1000.0) / 1000.0)
        val gb = (((bytes / 1000.0) / 1000.0) / 1000.0)
        val tb = ((((bytes / 1000.0) / 1000.0) / 1000.0) / 1000.0)

        return if (speed) formatSpeed(b, kb, mb, gb, tb, context)
        else formatVolume(b, kb, mb, gb, tb, context)
    }


    private fun formatSpeed(b: Double, kb: Double, mb: Double, gb: Double, tb: Double, context: Context): String {
        val dec: DecimalFormat = requireNotNull(formatter.get())
        return when {
            tb >= 1 -> context.getString(R.string.speed_terabytes_per_second, dec.format(tb))
            gb >= 1 -> context.getString(R.string.speed_gigabytes_per_second, dec.format(gb))
            mb >= 1 -> context.getString(R.string.speed_megabytes_per_second, dec.format(mb))
            kb >= 1 -> context.getString(R.string.speed_kilobytes_per_second, dec.format(kb))
            else -> context.getString(R.string.speed_bytes_per_second, dec.format(b))
        }
    }

    private fun formatVolume(b: Double, kb: Double, mb: Double, gb: Double, tb: Double, context: Context): String {
        val dec: DecimalFormat = requireNotNull(formatter.get())
        return when {
            tb >= 1 -> context.getString(R.string.traffic_volume_terabytes, dec.format(tb))
            gb >= 1 -> context.getString(R.string.traffic_volume_gigabytes, dec.format(gb))
            mb >= 1 -> context.getString(R.string.traffic_volume_megabytes, dec.format(mb))
            kb >= 1 -> context.getString(R.string.traffic_volume_kilobytes, dec.format(kb))
            else -> context.getString(R.string.traffic_volume_bytes, dec.format(b))
        }
    }
}
