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

import com.protonvpn.android.BuildConfig
import me.proton.core.util.kotlin.Logger
import me.proton.core.util.kotlin.LoggerLogTag

class CoreLogger : Logger {

    override fun log(tag: LoggerLogTag, message: String) {
        ProtonLogger.log("[${tag.name}] $message")
    }

    override fun e(tag: String, e: Throwable) {
        ProtonLogger.log(e.toString())
        if (BuildConfig.DEBUG)
            e.printStackTrace()
    }

    override fun e(tag: String, e: Throwable, message: String) {
        e(tag, e)
        ProtonLogger.log("[$tag] $message")
    }

    override fun i(tag: String, message: String) {
        ProtonLogger.log("[$tag] $message")
    }

    override fun i(tag: String, e: Throwable, message: String) {
        ProtonLogger.log("[$tag] $message")
        if (BuildConfig.DEBUG)
            e.printStackTrace()
    }

    override fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG)
            ProtonLogger.log("[$tag] $message")
    }

    override fun d(tag: String, e: Throwable, message: String) {
        d(tag, message)
        if (BuildConfig.DEBUG)
            e.printStackTrace()
    }

    override fun v(tag: String, message: String) {
        ProtonLogger.log("[$tag] $message")
    }

    override fun v(tag: String, e: Throwable, message: String) {
        v(tag, message)
        if (BuildConfig.DEBUG)
            e.printStackTrace()
    }
}
