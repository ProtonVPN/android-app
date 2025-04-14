/*
 * Copyright (c) 2019 Proton AG
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

import com.protonvpn.android.BuildConfig
import inet.ipaddr.IPAddressString
import inet.ipaddr.IPAddressStringParameters
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

    private val ipv4Pattern = Regex("(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])")
    private val ipv6Pattern =
        Regex("(?:^|(?<=\\s))(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))(?=\\s|\$)")

    fun String.maskAnyIP(): String {
        // Swapping of version name is necessary because it falls under IPv4 valid regex and we do not want to replace
        // our app version in logs
        return replace(BuildConfig.VERSION_NAME, "REPLACED_VERSION_REGEX")
            .replace(ipv4Pattern, "masked-ipv4")
            .replace(ipv6Pattern, "masked-ipv6")
            .replace("REPLACED_VERSION_REGEX", BuildConfig.VERSION_NAME)
    }

    fun byteArrayBuilder(block: DataOutputStream.() -> Unit): ByteArray {
        val byteStream = ByteArrayOutputStream()
        DataOutputStream(byteStream).use(block)
        return byteStream.toByteArray()
    }
}

private val ipStringParams = IPAddressStringParameters.Builder()
    .allowSingleSegment(false)
    .allowMask(false)
    .allowPrefixOnly(false)
    .allowEmpty(false)
    .allowWildcardedSeparator(false)
    .allow_inet_aton(false)
    .toParams()


fun String.isValidIp(allowIpv6: Boolean = true): Boolean =
    this.isNotBlank()
            && with(IPAddressString(this, ipStringParams)) {
        isValid && !isPrefixed && !isZero && !isLoopback && (allowIpv6 || isIPv4)
    } && this.trim() == this

fun String.isIPv6(): Boolean =
    this.isNotBlank() && IPAddressString(this).isIPv6

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
