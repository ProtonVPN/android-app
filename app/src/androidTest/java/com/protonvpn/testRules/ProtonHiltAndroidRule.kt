/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.testRules

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.TestSettings
import com.protonvpn.android.ProtonApplication
import com.protonvpn.mocks.MockRequestDispatcher
import com.protonvpn.mocks.MockWebServerCertificates
import com.protonvpn.mocks.TestApiConfig
import dagger.hilt.android.testing.HiltAndroidRule
import okhttp3.mockwebserver.MockWebServer
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Hilt injection in tests for ProtonApplication.
 *
 * It calls ProtonApplication.initDependencies() after Hilt components are initialized but before the
 * test is started.
 *
 * Use it instead of HiltAndroidRule.
 */
class ProtonHiltAndroidRule(
    testInstance: Any,
    private val apiConfig: TestApiConfig
) : TestRule {

    private val hiltAndroidRule = HiltAndroidRule(testInstance)

    private var mockRequestDispatcher: MockRequestDispatcher? = null

    val mockDispatcher: MockRequestDispatcher get() =
        checkNotNull(mockRequestDispatcher) { "mockDispatcher is only available for TestApiConfig.Mocked" }

    override fun apply(base: Statement, description: Description): Statement {
        val statement = object : Statement() {
            override fun evaluate() {
                val mockWebServer: MockWebServer? =
                    if (apiConfig is TestApiConfig.Mocked) startMockWebServer(apiConfig)
                    else null

                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    ApplicationProvider.getApplicationContext<ProtonApplication>().initDependencies()
                }

                base.evaluate()

                Log.d("ProtonHiltAndroidRule", "Test finished")
                mockWebServer?.shutdown()
                mockRequestDispatcher = null
                TestSettings.reset()
            }
        }
        return hiltAndroidRule.apply(statement, description)
    }

    fun inject() {
        hiltAndroidRule.inject()
    }

    private fun startMockWebServer(testApiConfig: TestApiConfig.Mocked): MockWebServer {
        mockRequestDispatcher = MockRequestDispatcher().apply {
            testApiConfig.addDefaultRules(this)
        }
        val serverCertificates = MockWebServerCertificates.getServerCertificates()
        val mockServer = MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            dispatcher = mockDispatcher
            start()
        }
        with(TestSettings) {
            handshakeCertificatesOverride = serverCertificates
            protonApiUrlOverride = mockServer.url("/") // Must be set before Hilt injects it.
        }
        return mockServer
    }
}
