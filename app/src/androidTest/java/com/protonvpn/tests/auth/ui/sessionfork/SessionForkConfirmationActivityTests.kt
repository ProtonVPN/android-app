/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.tests.auth.ui.sessionfork

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.auth.ui.sessionfork.SessionForkConfirmationActivity
import com.protonvpn.mocks.MockRuleBuilder
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.robots.mobile.SessionForkConfirmationRobot
import com.protonvpn.robots.mobile.SessionForkConfirmationRobot.verify
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testsHelper.UserDataHelper
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.core.auth.data.api.request.ForkSessionRequest
import me.proton.core.auth.data.api.response.ForkSessionResponse
import me.proton.core.featureflag.data.remote.resource.UnleashToggleResource
import me.proton.core.featureflag.data.remote.resource.UnleashVariantResource
import me.proton.core.featureflag.data.remote.response.GetUnleashTogglesResponse
import me.proton.core.network.domain.ResponseCodes
import me.proton.core.util.kotlin.deserialize
import me.proton.test.fusion.ui.compose.FusionComposeTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.test.assertNotNull

private const val FeatureFlagName = "QrCodeTvLogin"

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class SessionForkConfirmationActivityTests : FusionComposeTest() {

    private val QrCodeUrlIntent = Intent(Intent.ACTION_VIEW).apply {
        // This is an HTTPS URL that is by default opened in a browser.
        // The test application can't use AppLinks, so it needs to target the activity class
        // explicitly.
        setClass(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SessionForkConfirmationActivity::class.java,
        )
        addCategory(Intent.CATEGORY_DEFAULT)
        data = Uri.parse("https://account.proton.me/vpn/tv/code/1234ABCD")
    }

    private val apiConfig = TestApiConfig.Mocked(
        testUser = null,
        additionalRules = {
            featureFlagsResponseRule(FeatureFlagName to true)
        }
    )
    @get:Rule
    val protonRule = ProtonHiltAndroidRule(this, apiConfig)
    private var activityScenario: ActivityScenario<SessionForkConfirmationActivity>? = null

    private fun launchActivity() {
        activityScenario = ActivityScenario.launch(QrCodeUrlIntent)
    }

    @After
    fun tearDown() {
        activityScenario?.close()
    }

    @Test
    fun WhenNoUserLoggedInThenShowSignInScreen() {
        launchActivity()
        SessionForkConfirmationRobot.verify {
            assertSignInDisplayed()
        }
    }

    @Test
    fun WhenUserIsLoggedInThenShowConfirmationScreenImmediately() {
        setUser(TestUser.plusUser)
        launchActivity()
        SessionForkConfirmationRobot.verify {
            assertConfirmationDisplayed()
        }
    }

    @Test
    fun WhenBusinessUserIsLoggedInThenErrorIsShown() {
        setUser(TestUser.businessEssential)
        launchActivity()
        SessionForkConfirmationRobot.verify {
            assertErrorIsDisplayed()
        }
    }

    @Test
    fun WhenForkConfirmedTooFastThenMessageIsDisplayed() {
        setUser(TestUser.plusUser)
        launchActivity()
        SessionForkConfirmationRobot
            .confirmFork()
            .verify {
                assertTooSoonSnackIsDisplayed()
            }
    }

    @Test
    fun WhenForkConfirmedThenPostRequestIsSent() {
        setUser(TestUser.plusUser)
        protonRule.mockDispatcher.addRules {
            rule(post, path eq "/auth/v4/sessions/forks") {
                respond(200, ForkSessionResponse(1000, "selector"))
            }
        }
        launchActivity()
        SessionForkConfirmationRobot
            .verify {
                assertConfirmationDisplayed()
            }
        Thread.sleep(5_000) // Can't confirm too soon.
        SessionForkConfirmationRobot
            .confirmFork()
            .verify {
                assertForkSuccessIsDisplayed()
            }
        val forkRequest = protonRule.mockDispatcher.recordedRequests.lastOrNull {
            it.path?.contains("/auth/v4/sessions/forks") ?: false
        }
        assertNotNull(forkRequest)
        val requestBody = forkRequest.body.readString(UTF_8).deserialize<ForkSessionRequest>()
        assertEquals("android_tv-vpn", requestBody.childClientId)
        assertEquals("1234ABCD", requestBody.userCode)
        assertEquals(1L, requestBody.independent)
    }

    private fun MockRuleBuilder.featureFlagsResponseRule(
        vararg flags: Pair<String, Boolean>
    ) {
        val toggles = flags.map {
            UnleashToggleResource(
                name = it.first,
                variant = UnleashVariantResource(name = it.first, enabled = it.second)
            )
        }
        rule(get, path eq "/feature/v2/frontend") {
            respond(GetUnleashTogglesResponse(ResponseCodes.OK, toggles))
        }
    }

    private fun setUser(user: TestUser) {
        UserDataHelper().setUserData(user)
        protonRule.mockDispatcher.prependRules {
            rule(get, path eq "/vpn/v2") { respond(user.vpnInfoResponse) }
        }
    }
}