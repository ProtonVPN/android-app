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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class LokiClient {

    private val lokiApiUrl = "${BuildConfig.LOKI_ENDPOINT}loki/api/v1/push"

    fun pushLokiEntry(entry: JSONObject): Boolean {
        val requestBody = entry.toString().toRequestBody("application/json".toMediaType())
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(lokiApiUrl)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if(!response.isSuccessful){
            throw Exception(response.isSuccessful.toString())
        }
        return response.isSuccessful
    }

    fun createLokiEntry(metrics: Map<String, String>): JSONObject {
        val metricsJson = JSONObject(metrics)
        val timestamp = System.currentTimeMillis() * 1000000
        val values = JSONArray()
            .put(JSONArray().put(timestamp.toString()).put(metricsJson.toString()))

        val stream = JSONObject()
        stream.put("stream", getMetricLabels())
        stream.put("values", values)

        val payload = JSONObject()
        payload.put("streams", JSONArray().put(stream))

        return payload
    }

    private fun getMetricLabels(): JSONObject {
        val labels = mapOf(
            "workflow" to "main_measurements",
            "environment" to "prod",
            "platform" to "android",
            "product" to "VPN",
            "sli" to sliGroup
        )
        return JSONObject(labels)
    }

    companion object {
        var metrics: MutableMap<String, String> = mutableMapOf()
        var sliGroup: String = ""
    }
}