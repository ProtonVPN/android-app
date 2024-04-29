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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.protonvpn.android.release_tests.BuildConfig
import com.protonvpn.android.release_tests.data.TestConstants
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class LokiClient {

    private val lokiApiUrl = "${BuildConfig.LOKI_ENDPOINT}loki/api/v1/push"
    private val sharedLabels = mapOf(
        "workflow" to "main_measurements",
        "environment" to "prod",
        "platform" to "android",
        "product" to "VPN"
    )

    fun pushLokiEntry(entry: JSONObject): Boolean {
        val requestBody = entry.toString().toRequestBody("application/json".toMediaType())
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(lokiApiUrl)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception(response.body?.string())
        }
        return response.isSuccessful
    }

    @SuppressLint("NewApi")
    fun createLokiEntry(metrics: Map<String, String>, labels: JSONObject): JSONObject {
        val metricsJson = JSONObject(metrics)
        val timestampNano = with(Instant.now()) { "$epochSecond$nano" }
        val values = JSONArray()
            .put(
                JSONArray().put(timestampNano).put(metricsJson.toString()).put(getMetadata())
            )

        val stream = JSONObject()
        stream.put("stream", labels)
        stream.put("values", values)

        val payload = JSONObject()
        payload.put("streams", JSONArray().put(stream))

        return payload
    }

    fun createLogsEntry(metrics: Map<String, String>, labels: JSONObject): JSONObject {
        var values = JSONArray()
        for (metric in metrics) {
            values.put(JSONArray().put(metric.key).put(metric.value).put(getMetadata()))
        }

        val stream = JSONObject()
        stream.put("stream", labels)
        stream.put("values", values)

        val payload = JSONObject()
        payload.put("streams", JSONArray().put(stream))

        return payload
    }

    fun parseLogs(): MutableMap<String, String> {
        val logsMap = mutableMapOf<String, String>()
        val process =
            Runtime.getRuntime().exec("logcat -d -s ProtonLogger -v epoch,printable,UTC,usec")
        val inputStream = process.inputStream
        inputStream.bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                val timestampNano = getTimestampFromLogcat(line)
                val logMessage = line.substringAfter("ProtonLogger:").trim()
                logsMap[timestampNano] = logMessage
            }
        }
        return logsMap
    }

    fun getMetricLabels(): JSONObject {
        val labels = mapOf(
            "sli" to sliGroup
        ) + sharedLabels
        return JSONObject(labels)
    }

    fun getLogsLabels(): JSONObject {
        return JSONObject(sharedLabels)
    }

    private fun getMetadata(): JSONObject {
        val labels = mapOf(
            "id" to id,
            "os_version" to Build.VERSION.RELEASE,
            "app_version" to getAppVersion(),
            "build_commit_sha1" to BuildConfig.CI_COMMIT_SHORT_SHA,
            "device_model" to Build.MODEL
        )
        return JSONObject(labels)
    }

    private fun getAppVersion(): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = context.packageManager.getPackageInfo(TestConstants.TEST_PACKAGE, 0)
        return packageInfo.versionName
    }

    private fun getTimestampFromLogcat(line: String): String {
        return line
            .trim()
            .split(" ")[0]
            .replace(".", "")
            // Loki expects nano seconds, so we have to add additional 0
            .plus("000")
    }

    companion object {
        var metrics: MutableMap<String, String> = mutableMapOf()
        var sliGroup: String = ""
        var id: String = ""
    }
}