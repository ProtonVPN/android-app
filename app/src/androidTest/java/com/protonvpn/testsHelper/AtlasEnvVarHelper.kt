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

package com.protonvpn.testsHelper

import com.protonvpn.BuildConfig
import me.proton.core.configuration.EnvironmentConfigurationDefaults
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AtlasEnvVarHelper {

    private val forceCaptchaResponse = """\"{\\\"code\\\": 2000, \\\"result\\\": \\\"captcha\\\"}\""""

    fun forceCaptchaOnLogin(){
        postAtlasEnvVariable(generateFingerprintRequestBody(forceCaptchaResponse))
    }

    fun clearEnvVars(){
        postAtlasEnvVariable("")
    }

    inline fun withAtlasEnvVar(variableOverride: AtlasEnvVarHelper.() -> Unit, block: () -> Unit) {
        try{
            variableOverride()
            block()
        }
        finally{
            clearEnvVars()
        }
    }

    private fun generateFingerprintRequestBody(response: String): String {
        return """{"env":"FINGERPRINT_RESPONSE=$response"}"""
    }

    private fun postAtlasEnvVariable(jsonBody: String) {
        val url = "${EnvironmentConfigurationDefaults.baseUrl}/internal/system"

        val mediaType = "application/json".toMediaType()

        val client = OkHttpClient()

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(mediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("x-atlas-secret", EnvironmentConfigurationDefaults.proxyToken)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Error: ${response.body?.string()}")
            }
        }
    }
}