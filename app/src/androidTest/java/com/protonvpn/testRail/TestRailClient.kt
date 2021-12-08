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

import android.os.Build
import com.protonvpn.TestSettings
import com.protonvpn.android.BuildConfig
import com.protonvpn.data.DefaultData
import org.joda.time.DateTime

class TestRailClient {

    private val data = HashMap<Any?, Any?>()
    private val client: ApiClient

    fun createTestRun(): String {
        data["name"] =
            "${BuildConfig.CI_BRANCH_NAME} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) " + DateTime.now()
        data["suite_id"] = DefaultData.ANDROID_TESTRAIL_ID
        data["description"] = BuildConfig.CI_COMMIT_MESSAGE
        val newRun = client.sendPost("add_run/" + DefaultData.ANDROID_TESTRAIL_ID, data)
        return newRun["id"].toString()
    }

    fun addResultForTestCase(testCaseId: Long, status: Int, comment: String, testRunId: String?) {
        data["status_id"] = status
        data["comment"] = comment
        client.sendPost("add_result_for_case/$testRunId/$testCaseId", data)
    }

    fun shouldReport(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (BuildConfig.CI_BRANCH_NAME == "development" || BuildConfig.CI_BRANCH_NAME == "master" && TestSettings.testRailReportingUsed) {
                return true
            }
        }
        return false
    }

    init {
        val email = BuildConfig.TESTRAIL_CREDENTIALS.split(":")[0]
        val apiKey = BuildConfig.TESTRAIL_CREDENTIALS.split(":")[1]
        client = ApiClient(DefaultData.TESTRAIL_PROJECT_URL, email, apiKey)
    }
}