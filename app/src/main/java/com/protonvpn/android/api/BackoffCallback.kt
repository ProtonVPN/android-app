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
package com.protonvpn.android.api

import android.os.Handler
import com.protonvpn.android.components.AuthorizeException
import com.protonvpn.android.utils.Log
import retrofit2.Call
import retrofit2.Callback

abstract class BackoffCallback<T> : Callback<T> {
    private var retryCount = 0

    abstract fun onStart()

    override fun onFailure(call: Call<T>, t: Throwable) {
        if (!call.isCanceled) {
            if (retryCount == 0) {
                Log.exception(t)
            }
            retryCount++
            if (retryCount <= RETRY_COUNT && t !is AuthorizeException) {
                val expDelay =
                        (RETRY_DELAY * Math.pow(2.0, Math.max(0, retryCount - 1).toDouble())).toInt()
                Handler().postDelayed({ retry(call) }, expDelay.toLong())
            } else {
                onFailedAfterRetry(t)
            }
        }
    }

    private fun retry(call: Call<T>) {
        call.clone().enqueue(this)
    }

    abstract fun onFailedAfterRetry(t: Throwable)

    companion object {
        private const val RETRY_COUNT = 2
        private const val RETRY_DELAY = 200.0
    }
}
