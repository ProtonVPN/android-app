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

package com.protonvpn.testsHelper

import com.protonvpn.annotations.TestID
import com.protonvpn.testRail.TestRailClient
import com.protonvpn.testSuites.MobileBlackSuite
import com.protonvpn.testSuites.MobileMainSuite
import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

class ProtonReportingRunListener : RunListener() {

    private lateinit var testDescription: String
    private var runId: String? = null
    private var currentTestStatusId: Int = PASSED_ID
    private val testRailClient = TestRailClient()

    override fun testSuiteStarted(description: Description?) {
        super.testSuiteStarted(description)
        if(description?.testClass == MobileBlackSuite::class.java || description?.testClass == MobileMainSuite::class.java){
            if(testRailClient.shouldReport()){
                runId = testRailClient.createTestRun()
            }
        }
    }

    override fun testFailure(failure: Failure?) {
        super.testFailure(failure)
        currentTestStatusId = FAILED_ID
        testDescription = failure!!.exception.message.toString()
    }

    override fun testStarted(description: Description?) {
        super.testStarted(description)
        currentTestStatusId = PASSED_ID
        testDescription = "Test Passed"
    }

    override fun testFinished(description: Description?) {
        super.testFinished(description)
        if (testRailClient.shouldReport() && !runId.isNullOrBlank()) {
            description?.annotations?.forEach {
                if (it is TestID) {
                    testRailClient.addResultForTestCase(
                        it.id,
                        currentTestStatusId,
                        testDescription,
                        runId
                    )
                }
            }
        }
    }

    override fun testSuiteFinished(description: Description?) {
        super.testSuiteFinished(description)
        if(description?.testClass == MobileBlackSuite::class.java || description?.testClass == MobileMainSuite::class.java){
            if(testRailClient.shouldReport()){
                runId = null
            }
        }
    }

    companion object {
        private const val PASSED_ID: Int = 1
        private const val FAILED_ID: Int = 5
    }
}
