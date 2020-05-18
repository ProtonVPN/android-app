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
package com.protonvpn.android.api

import android.content.Context
import com.fasterxml.jackson.core.JsonProcessingException
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.R
import com.protonvpn.android.components.ErrorBodyException
import com.protonvpn.android.models.login.ErrorBody
import com.protonvpn.android.utils.ConnectionTools
import com.protonvpn.android.utils.ProtonLogger
import retrofit2.Response
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

sealed class ApiResult<out T> {
    class Success<T>(val response: Response<T>) : ApiResult<T>() {
        init { require(response.isSuccessful) }
        val value: T get() = response.body()!!
    }

    abstract class Error : ApiResult<Nothing>() {
        open fun getMessage(context: Context): String =
                context.getString(R.string.loaderErrorGeneric)

        abstract fun getDebugMessage(): String
    }

    open class ErrorResponse(val httpCode: Int, val body: String) : Error() {
        override fun getDebugMessage() = "$body ($httpCode)"
    }

    class ErrorBodyResponse(httpCode: Int, val errorBody: ErrorBody) : ErrorResponse(httpCode, errorBody.error) {
        override fun getMessage(context: Context): String = errorBody.error
        override fun getDebugMessage() = "${errorBody.error} (${errorBody.code})"
    }

    class Failure(val exception: Exception) : Error() {
        override fun getMessage(context: Context): String =
            if (exception is TimeoutException || exception is SocketTimeoutException)
                context.getString(R.string.loaderErrorTimeout)
            else
                super.getMessage(context)

        override fun getDebugMessage(): String = exception.localizedMessage ?: exception.toString()
    }

    val valueOrNull get() = (this as? Success)?.value

    fun isPotentialBlocking(context: Context) =
        this is Failure && ConnectionTools.isNetworkAvailable(context)

    companion object {
        suspend fun <T> tryWrap(block: suspend () -> Response<T>): ApiResult<T> = try {
            val response = block()
            if (response.isSuccessful)
                Success(response)
            else
                ErrorResponse(response.code(), response.errorBody()!!.string())
        } catch (e: ErrorBodyException) {
            ErrorBodyResponse(e.httpCode, e.errorBody)
        } catch (e: IOException) {
            if (e is JsonProcessingException) {
                ProtonLogger.log(stackTraceString(e))

                // Crash on debug to make sure we find about unexpected responses asap
                if (BuildConfig.DEBUG)
                    error("Error parsing API response: $e")
            }
            Failure(e)
        } catch (e: TimeoutException) {
            Failure(e)
        }

        private fun stackTraceString(e: Exception) =
            StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
    }
}
