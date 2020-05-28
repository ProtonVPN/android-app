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

import com.protonvpn.android.BuildConfig
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.bus.ForcedLogout
import com.protonvpn.android.components.ErrorBodyException
import com.protonvpn.android.debug.DebugInfo
import com.protonvpn.android.models.login.ErrorBody
import com.protonvpn.android.models.login.LoginBody
import com.protonvpn.android.models.login.RefreshBody
import com.protonvpn.android.utils.Json
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.User
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.joda.time.DateTime
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

abstract class ProtonApiBackend(override val baseUrl: String) : ApiBackendRetrofit<ProtonVPNRetrofit> {

    companion object {
        private const val HTTP_UNAUTHORIZED_401 = 401
        private const val ERROR_CODE_TOKEN_REFRESH_FAILED = 10013
        private const val ERROR_CODE_FORCE_UPDATE = 5003
    }

    internal lateinit var okClient: OkHttpClient
    private lateinit var vpnAPI: ProtonVPNRetrofit

    // Need to be called by child class
    fun initialize() {
        require(baseUrl.endsWith('/'))

        val converterFactory = JacksonConverterFactory.create(Json.MAPPER)

        val httpClientBuilder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .addInterceptor(Interceptor { chain ->
                    val original = chain.request()
                    chain.proceed(prepareHeaders(original).build())
                }).addInterceptor(Interceptor { chain ->
                    interceptErrors(chain)
                })
                .addInterceptor(RequestsInterceptor())
                .addInterceptor(UserAgentInterceptor())
        if (BuildConfig.DEBUG) {
            httpClientBuilder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
        }

        setupOkBuilder(httpClientBuilder)

        okClient = httpClientBuilder.build()
        vpnAPI = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okClient)
                .addConverterFactory(converterFactory)
                .build()
                .create(ProtonVPNRetrofit::class.java)
    }

    abstract fun setupOkBuilder(builder: OkHttpClient.Builder)

    @Throws(IOException::class)
    private fun interceptErrors(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        var errorBody: ErrorBody? = null
        try {
            val responseBodyCopy = response.peekBody(Long.MAX_VALUE)
            val copy = responseBodyCopy.string()
            if (request.url.toString().endsWith("/vpn/logicals")) {
                Storage.save(DebugInfo(DateTime(), copy))
            }
            errorBody = Json.MAPPER.readValue(copy, ErrorBody::class.java)
        } catch (ignored: IOException) {
        }
        if (response.code == HTTP_UNAUTHORIZED_401) {
            if (request.url.encodedPath != "/auth/refresh")
                return refreshTokenAndProceed(request, chain)
        }
        if (errorBody != null) {
            // TODO Move forced upgrade to new event and show some kind of message to user after log out
            if (errorBody.code == ERROR_CODE_TOKEN_REFRESH_FAILED || errorBody.code == ERROR_CODE_FORCE_UPDATE) {
                EventBus.postOnMain(ForcedLogout.INSTANCE)
            }
            Log.d(errorBody.error)
            throw ErrorBodyException(response.code, errorBody)
        }
        return response
    }

    private fun prepareHeaders(original: Request): Request.Builder {
        val request = original.newBuilder()
                .header("x-pm-appversion", "AndroidVPN_" + BuildConfig.VERSION_NAME)
                .header("x-pm-apiversion", "3")
                .header("x-pm-locale", Locale.getDefault().language)
                .header("Accept", "application/vnd.protonmail.v1+json")
                .method(original.method, original.body)
        if (!User.getAccessToken().isEmpty() && !User.getUuid().isEmpty()) {
            request.addHeader("x-pm-uid", User.getUuid())
            request.addHeader("Authorization", User.getAccessToken())
        }
        return request
    }

    @Throws(IOException::class)
    private fun refreshTokenAndProceed(request: Request, chain: Interceptor.Chain): Response {
        val call = vpnAPI.postRefresh(RefreshBody())
        Storage.delete(LoginBody::class.java)
        val body = call.execute().body()
        Storage.save(body)
        val newRequest = request.newBuilder()
                .header("Authorization", User.getAccessToken())
                .header("x-pm-uid", User.getUuid())
                .build()
        return chain.proceed(newRequest)
    }

    suspend fun ping() = ApiResult.tryWrap { vpnAPI.ping() }

    override suspend fun <T> call(
        callFun: suspend (ProtonVPNRetrofit) -> retrofit2.Response<T>
    ) = ApiResult.tryWrap { callFun(vpnAPI) }
}
