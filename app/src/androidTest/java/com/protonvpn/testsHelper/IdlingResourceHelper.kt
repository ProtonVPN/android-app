/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.testsHelper

import androidx.annotation.CheckResult
import androidx.test.espresso.IdlingResource
import okhttp3.OkHttpClient
import okhttp3.Dispatcher
import java.lang.NullPointerException

class IdlingResourceHelper private constructor(
    private val name: String,
    private val dispatcher: Dispatcher
) : IdlingResource {

    @Volatile
    var callback: IdlingResource.ResourceCallback? = null

    override fun getName(): String = name

    override fun isIdleNow(): Boolean {
        val idle = dispatcher.runningCallsCount() == 0
        if (idle && callback != null) {
            callback!!.onTransitionToIdle()
        }
        return idle
    }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
        this.callback = callback
    }

    companion object {
        @CheckResult  // Extra guards as a library.
        fun create(name: String, client: OkHttpClient): IdlingResourceHelper {
            if (name == null) {
                throw NullPointerException("name == null")
            }
            if (client == null) {
                throw NullPointerException("client == null")
            }
            return IdlingResourceHelper(name, client.dispatcher)
        }
    }

    init {
        dispatcher.idleCallback = Runnable {
            val callback = callback
            callback?.onTransitionToIdle()
        }
    }
}