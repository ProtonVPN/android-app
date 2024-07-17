/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.android.release_tests.helpers

import com.protonvpn.android.release_tests.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object TestApiClient {
    private const val PROD_DOMAIN = "https://api.protonvpn.ch/"
    private const val BTI_CONTROLLER_IP = BuildConfig.BTI_CONTROLLER_URL

    fun setBtiScenario(endpoint: String) {
        sendGetRequest("$BTI_CONTROLLER_IP$endpoint", getUnsafeOkHttpClient())
    }

    fun getRandomServer(): String {
        val logicalServers = getLogicals().getJSONArray("LogicalServers")
        val filteredServers = (0 until logicalServers.length())
            .map { logicalServers.getJSONObject(it) }
            .filter { server ->
                val name = server.getString("Name")
                !name.contains("IS-") && !name.contains("CH-") && !name.contains("SE-")
                        && !name.contains("TOR")
            }
        val randomServer = filteredServers.random()
        return randomServer.getString("Name")
    }

    private fun getLogicals(): JSONObject {
        val responseBody = sendGetRequest("${PROD_DOMAIN}vpn/logicals", OkHttpClient())
        return JSONObject(responseBody)
    }

    private fun sendGetRequest(url: String, client: OkHttpClient): String? {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception(response.body?.string())
        }

        return response.body?.string()
    }

    private fun getUnsafeOkHttpClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        val sslSocketFactory = sslContext.socketFactory

        val builder = OkHttpClient.Builder()
        builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
        builder.hostnameVerifier { _, _ -> true }

        return builder.build()
    }
}