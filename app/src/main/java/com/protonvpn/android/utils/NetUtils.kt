/*
 * Copyright (c) 2019 Proton Technologies AG
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

import me.proton.core.network.domain.ApiResult
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.lang.Long.min
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object NetUtils {

    // Strips (replace with 0) the last segment of IPv4 and network interface part of IPv6 (long format) addresses
    fun stripIP(ip: String): String {
        return """(.*)\.[0-9]+""".toRegex().matchEntire(ip)?.let {
            "${it.groups[1]!!.value}.0"
        }
            ?: """([0-9a-fA-F]+:[0-9a-fA-F]+:[0-9a-fA-F]+:[0-9a-fA-F]+):.*""".toRegex().matchEntire(ip)?.let {
                "${it.groups[1]!!.value}:0:0:0:0"
            } ?: ip
    }

    fun byteArrayBuilder(block: DataOutputStream.() -> Unit): ByteArray {
        val byteStream = ByteArrayOutputStream()
        DataOutputStream(byteStream).use(block)
        return byteStream.toByteArray()
    }
}

fun jitterMs(
    baseMs: Long,
    diff: Float = .2f,
    maxMs: Long = TimeUnit.HOURS.toMillis(1),
    random: Random = Random
): Long {
    val rangeMs = min(maxMs, (diff * baseMs).toLong())
    return baseMs + random.nextLong(rangeMs)
}

fun ApiResult.Error.displayText(): String? =
    (this as? ApiResult.Error.Http)?.proton?.error ?: cause?.localizedMessage
