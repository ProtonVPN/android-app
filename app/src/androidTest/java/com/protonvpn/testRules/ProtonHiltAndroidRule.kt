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


import android.app.Application
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.IdlingRegistry
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.TestSettings
import com.protonvpn.android.ProtonApplication
import com.protonvpn.mocks.MockRequestDispatcher
import com.protonvpn.mocks.MockWebServerCertificates
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.testsHelper.EspressoDispatcherProvider
import com.protonvpn.testsHelper.IdlingResourceHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.proton.core.network.data.di.SharedOkHttpClient
import me.proton.core.util.kotlin.DispatcherProvider
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit

/**
 * Hilt injection in tests for ProtonApplication.
 *
 * It performs setup by initializing Hilt, idling resources, mock web server (if requested) and initializes the
 * application (by calling ProtonApplication.initDependencies) before the test is started.
 *
 * Use it instead of HiltAndroidRule.
 *
 * @param testInstance
 * @param apiConfig - API configuration: either use the backend or a mock web server
 * @param deferAppStartup - (default: false) if true the rule doesn't initialize the application. The test must call
 *                          startApplicationAndWaitForIdle(). Deferring startup allows tests to do some additional
 *                          setup, e.g. add mock web server rules.
 *                          Be careful not to initialize the application early by starting an activity or using rules
 *                          like SetLoggedInUserRule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProtonHiltAndroidRule(
    testInstance: Any,
    private val apiConfig: TestApiConfig,
    private val deferAppStartup: Boolean = false
) : TestRule {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HiltEntryPoint {
        fun dispatcherProvider(): DispatcherProvider
        @SharedOkHttpClient
        fun okHttpClient(): OkHttpClient
    }

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

                val hilt = EntryPointAccessors.fromApplication(
                    ApplicationProvider.getApplicationContext<Application>(),
                    HiltEntryPoint::class.java
                )
                IdlingPolicies.setIdlingResourceTimeout(45, TimeUnit.SECONDS)
                installIdlingResources(hilt.dispatcherProvider() as EspressoDispatcherProvider, hilt.okHttpClient())

                if (!deferAppStartup)
                    startApplicationAndWaitForIdle()

                base.evaluate()

                Espresso.onIdle() // Wait for any pending requests to finish before shutting down the mock web server.
                Log.d("ProtonHiltAndroidRule", "Test finished")
                mockWebServer?.shutdown()
                mockRequestDispatcher = null
                TestSettings.reset()
                uninstallIdlingResources()
            }
        }
        return hiltAndroidRule.apply(statement, description)
    }

    fun inject() {
        hiltAndroidRule.inject()
    }

    fun startApplicationAndWaitForIdle() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ApplicationProvider.getApplicationContext<ProtonApplication>().initDependencies()
        }
        Espresso.onIdle()
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

    private fun installIdlingResources(dispatcherProvider: EspressoDispatcherProvider, okHttpClient: OkHttpClient) {
        val registry = IdlingRegistry.getInstance()
        registry.register(dispatcherProvider.idlingResource)
        registry.register(IdlingResourceHelper.create("OkHttp", okHttpClient))
        Dispatchers.setMain(dispatcherProvider.Main)
    }

    private fun uninstallIdlingResources() {
        with(IdlingRegistry.getInstance()) { resources.forEach { unregister(it) } }
        Dispatchers.resetMain()
    }
}
