/*
 *  Copyright (c) 2021 Proton AG
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

package com.protonvpn.testRail

import com.protonvpn.android.utils.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.*

class ApiClient(private val baseUrl: String, private val email: String, private val apiKey: String) {

    fun sendPost(uri: String, data: Any?): JSONObject {
        val requestBody = Json.encodeToString(data)
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .header("Authorization", "Basic " + getEncodedAuthString(email, apiKey))
            .url(baseUrl + uri)
            .post(requestBody)
            .build()

        val response = OkHttpClient().newCall(request).execute()
        val responseBody = response.body!!.string()
        Log.i(baseUrl + uri + " returned status code " + response.code)
        Log.i("Response body: $responseBody")
        return JSONObject(responseBody)
    }

    private fun getEncodedAuthString(user: String?, password: String?): String {
        return String(Base64.getEncoder().encode("$user:$password".toByteArray()))
    }
}
