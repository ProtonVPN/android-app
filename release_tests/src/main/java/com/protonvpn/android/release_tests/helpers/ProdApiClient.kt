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

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.random.Random

class ProdApiClient {

    private val prodDomain = "https://api.protonvpn.ch/"
    private fun getLogicals(): JSONObject {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("${prodDomain}vpn/logicals")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception(response.body?.string())
        }
        return JSONObject(response.body!!.string())
    }

    fun getRandomServer(): String {
        val logicalServers = getLogicals().getJSONArray("LogicalServers")
        val filteredServers = (0 until logicalServers.length())
            .map { logicalServers.getJSONObject(it) }
            .filter { server ->
                val name = server.getString("Name")
                !name.contains("IS-") && !name.contains("CH-") && !name.contains("SE-")
            }
        val randomServer = filteredServers.random()
        return randomServer.getString("Name")
    }
}