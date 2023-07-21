/*
 * Copyright (c) 2020 Proton Technologies AG
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

import com.protonvpn.android.logging.ApiLogError
import com.protonvpn.android.logging.ApiLogRequest
import com.protonvpn.android.logging.ApiLogResponse
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import me.proton.core.humanverification.presentation.LogTag
import me.proton.core.crypto.common.keystore.LogTag as KeystoreLogTag
import me.proton.core.network.domain.LogTag as NetworkLogTag
import me.proton.core.util.kotlin.Logger
import me.proton.core.util.kotlin.LoggerLogTag

// Core logs full response body in debug, truncate it.
private const val MAX_DEBUG_MSG_LENGTH = 500

class VpnCoreLogger : Logger {

    override fun log(tag: LoggerLogTag, message: String) {
        when (tag) {
            AccountLogTag.SESSION_REFRESH ->
                ProtonLogger.logCustom(LogCategory.API, message)
            AccountLogTag.SESSION_REQUEST ->
                ProtonLogger.logCustom(LogCategory.API, message)
            AccountLogTag.SESSION_FORCE_LOGOUT ->
                ProtonLogger.logCustom(LogCategory.API, message)
            NetworkLogTag.SERVER_TIME_PARSE_ERROR ->
                ProtonLogger.logCustom(LogLevel.ERROR, LogCategory.API, message)
            NetworkLogTag.API_REQUEST ->
                ProtonLogger.log(ApiLogRequest, message)
            NetworkLogTag.API_RESPONSE ->
                ProtonLogger.log(ApiLogResponse, message)
            NetworkLogTag.API_ERROR ->
                ProtonLogger.log(ApiLogError, message)
            LogTag.HV_REQUEST_ERROR ->
                ProtonLogger.logCustom(LogLevel.ERROR, LogCategory.API, message)
            else -> {
                DebugUtils.debugAssert("Unknown log tag. Update this mapping.") { true }
                ProtonLogger.logCustom(LogCategory.APP, "[${tag.name}] $message")
            }
        }
    }

    override fun e(tag: String, e: Throwable) {
        e(tag, e, "")
    }

    override fun e(tag: String, e: Throwable, message: String) {
        ProtonLogger.logCustom(LogLevel.ERROR, categoryForTag(tag), messageWithError(message, e))
    }

    override fun i(tag: String, message: String) {
        ProtonLogger.logCustom(LogLevel.INFO, categoryForTag(tag), message)
    }

    override fun i(tag: String, e: Throwable, message: String) {
        ProtonLogger.logCustom(LogLevel.INFO, categoryForTag(tag), messageWithError(message, e))
    }

    override fun d(tag: String, message: String) {
        ProtonLogger.logCustom(LogLevel.DEBUG, categoryForTag(tag), message.take(MAX_DEBUG_MSG_LENGTH))
    }

    override fun d(tag: String, e: Throwable, message: String) {
        ProtonLogger.logCustom(LogLevel.DEBUG, categoryForTag(tag), messageWithError(message, e))
    }

    override fun v(tag: String, message: String) {
        ProtonLogger.logCustom(LogLevel.TRACE, categoryForTag(tag), message)
    }

    override fun v(tag: String, e: Throwable, message: String) {
        ProtonLogger.logCustom(LogLevel.TRACE, categoryForTag(tag), messageWithError(message, e))
    }

    private fun messageWithError(message: String, e: Throwable) =
        "$message\n${e.stackTraceToString()}"

    private fun categoryForTag(tag: String) = when (tag) {
        KeystoreLogTag.KEYSTORE_INIT, KeystoreLogTag.KEYSTORE_ENCRYPT, KeystoreLogTag.KEYSTORE_DECRYPT ->
            LogCategory.SECURE_STORE
        NetworkLogTag.DEFAULT ->
            LogCategory.API
        else -> {
            DebugUtils.debugAssert("Unknown log tag. Update this mapping.") { true }
            LogCategory.APP
        }
    }
}
