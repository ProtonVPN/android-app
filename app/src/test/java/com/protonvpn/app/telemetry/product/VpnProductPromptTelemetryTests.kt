/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.app.telemetry.product

import com.protonvpn.android.telemetry.TelemetryEventData
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.android.telemetry.product.VpnProductPromptTelemetry
import com.protonvpn.mocks.FakeCommonDimensions
import com.protonvpn.mocks.TestTelemetryReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class VpnProductPromptTelemetryTests {

    private lateinit var testScope: TestScope

    private lateinit var testTelemetryReporter: TestTelemetryReporter

    private lateinit var vpnProductPromptTelemetry: VpnProductPromptTelemetry

    private val promptTypes = listOf(
        VpnProductPromptTelemetry.PromptType.Announcement to "announcement",
        VpnProductPromptTelemetry.PromptType.Education to "education",
        VpnProductPromptTelemetry.PromptType.FeatureDiscovery to "feature_discovery",
    )

    private val promptContexts = listOf(
        VpnProductPromptTelemetry.PromptContext.ConnectionPreferencesAdoption to "connection_preferences_tooltip",
        VpnProductPromptTelemetry.PromptContext.ConnectionPreferencesFirstConnection to "connection_preferences_first_connection",
    )

    private val promptActions = listOf(
        VpnProductPromptTelemetry.PromptAction.Configure to "configure",
        VpnProductPromptTelemetry.PromptAction.Dismiss to "dismiss",
        VpnProductPromptTelemetry.PromptAction.LearnMore to "learn_more",
    )

    private val userTier = "paid"

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()

        Dispatchers.setMain(dispatcher = testDispatcher)

        testScope = TestScope(context = testDispatcher)

        testTelemetryReporter = TestTelemetryReporter()

        vpnProductPromptTelemetry = VpnProductPromptTelemetry(
            telemetryHelper = TelemetryFlowHelper(
                mainScope = testScope.backgroundScope,
                telemetry = testTelemetryReporter,
            ),
            commonDimensions = FakeCommonDimensions(dimensions = mapOf("user_tier" to userTier)),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `WHEN tracking prompt display THEN telemetry event is sent`() = testScope.runTest {
        promptTypes.forEach { (promptType, promptTypeValue) ->
            promptContexts.forEach { (promptContext, promptContextValue) ->
                val expectedTelemetryEvent = createTelemetryEventData(
                    eventName = "product_prompt_display",
                    dimensions = mapOf(
                        "prompt_type" to promptTypeValue,
                        "prompt_context" to promptContextValue,
                        "user_tier" to userTier,
                    )
                )

                vpnProductPromptTelemetry.trackPromptDisplayed(type = promptType, context = promptContext)

                testTelemetryReporter.collectedEvents.first().let { telemetryEventData ->
                    assertEquals(
                        expected = expectedTelemetryEvent,
                        actual = telemetryEventData,
                        message = "Failed for prompt type: $promptType, prompt context: $promptContext",
                    )
                }

                testTelemetryReporter.reset()
            }
        }
    }

    @Test
    fun `WHEN tracking prompt action THEN telemetry event is sent`() = testScope.runTest {
        promptTypes.forEach { (promptType, promptTypeValue) ->
            promptContexts.forEach { (promptContext, promptContextValue) ->
                promptActions.forEach { (promptAction, promptActionValue) ->
                    val expectedTelemetryEvent = createTelemetryEventData(
                        eventName = "product_prompt_action",
                        dimensions = mapOf(
                            "prompt_type" to promptTypeValue,
                            "prompt_context" to promptContextValue,
                            "prompt_action" to promptActionValue,
                            "user_tier" to userTier,
                        )
                    )

                    vpnProductPromptTelemetry.trackPromptAction(
                        type = promptType,
                        context = promptContext,
                        action = promptAction
                    )

                    testTelemetryReporter.collectedEvents.first().let { telemetryEventData ->
                        assertEquals(
                            expected = expectedTelemetryEvent,
                            actual = telemetryEventData,
                            message = "Failed for prompt type: $promptType, prompt context: $promptContext, prompt action: $promptAction",
                        )
                    }

                    testTelemetryReporter.reset()
                }
            }
        }
    }

    private fun createTelemetryEventData(
        eventName: String,
        dimensions: Map<String, String>,
    ) = TelemetryEventData(
        measurementGroup = "vpn.any.product_prompts",
        eventName = eventName,
        dimensions = dimensions,
    )

}
