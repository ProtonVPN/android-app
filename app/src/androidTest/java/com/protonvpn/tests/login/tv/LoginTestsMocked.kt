package com.protonvpn.tests.login.tv

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.android.appconfig.SessionForkSelectorResponse
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.android.tv.login.TvLoginViewModel
import com.protonvpn.android.tv.login.TvQrLoginActivity
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.robots.tv.LegacyTvLoginRobot
import com.protonvpn.robots.tv.TvLoginRobot
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testsHelper.UserDataHelper
import com.protonvpn.testsHelper.featureFlagsResponseRule
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.core.auth.data.api.response.ScopesResponse
import me.proton.core.key.data.api.response.AddressResponse
import me.proton.core.key.data.api.response.AddressesResponse
import me.proton.core.key.data.api.response.UserResponse
import me.proton.core.key.data.api.response.UsersResponse
import me.proton.core.user.domain.entity.AddressType
import me.proton.test.fusion.ui.compose.FusionComposeTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val FORK_SELECTOR = "fork_selector"
private const val FORK_USER_CODE = "1234ABCD"
private const val FEATURE_FLAG_NAME = "QrCodeTvLogin"

/**
 * [LoginTestsMocked] Contains all tests related to Login actions.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class LoginTestsMocked : FusionComposeTest() {

    private val user = TestUser.plusUser

    val scopes = listOf("self", "user", "loggedin", "vpn")
    // Use JSON text because the VPN and Account code expect different sets of fields.
    // When the feature flag is removed, it'll be possible to switch to GetForkedSessionResponse.
    val forkedSessionResponse = """
        {
            "ExpiresIn": 864000,
            "TokenType": "Bearer",
            "AccessToken": "AccessToken",
            "RefreshToken": "RefreshToken",
            "UID": "UID",
            "Payload": "Payload",
            "LocalID": 0,
            "UserID": "%s",
            "Scopes": [%s]
        }
    """.trimIndent().format(user.vpnUser.userId.id, scopes.joinToString { "\"$it\"" })

    // Login tests start with mock API in logged out state.
    private val mockApiConfig = TestApiConfig.Mocked(user) {
        rule(get, path eq "/auth/v4/sessions/forks") {
            respond(SessionForkSelectorResponse(FORK_SELECTOR, FORK_USER_CODE))
        }

        rule(get, path eq "/auth/v4/sessions/forks/$FORK_SELECTOR") {
            respond(TvLoginViewModel.HTTP_CODE_KEEP_POLLING)
        }

        rule(get, path eq "/core/v4/users") {
            val userResponse = createUserResponse(user.vpnUser.userId.id)
            respond(UsersResponse(userResponse))
        }
        rule(get, path eq "/core/v4/addresses") {
            respond(AddressesResponse(listOf(createAddressResponse())))
        }
        rule(get, path eq "/auth/v4/scopes") {
            respond(ScopesResponse(scopes))
        }
        featureFlagsResponseRule(FEATURE_FLAG_NAME to true)
    }

    private var activityScenario: ActivityScenario<*>? = null

    @get:Rule
    val protonHiltRule = ProtonHiltAndroidRule(this, mockApiConfig)

    private val loginRobot = LegacyTvLoginRobot()
    private lateinit var userDataHelper: UserDataHelper

    @Before
    fun setUp() {
        protonHiltRule.inject()
        userDataHelper = UserDataHelper()
        activityScenario = null
    }

    @After
    fun teardown() {
        activityScenario?.close()
    }

    @Test
    fun loginHappyPath() {
        protonHiltRule.mockDispatcher.prependRules {
            featureFlagsResponseRule(FEATURE_FLAG_NAME to false)
        }
        activityScenario = ActivityScenario.launch(TvLoginActivity::class.java)
        loginRobot
            .signIn()
        protonHiltRule.mockDispatcher.prependRules {
            rule(get, path eq "/auth/v4/sessions/forks/$FORK_SELECTOR") {
                respond(forkedSessionResponse)
            }
        }
        loginRobot
            .waitUntilLoggedIn()
            .verify { userIsLoggedIn() }
    }

    @Test
    fun loginUserCodeIsDisplayed() {
        protonHiltRule.mockDispatcher.prependRules {
            featureFlagsResponseRule(FEATURE_FLAG_NAME to false)
        }
        activityScenario = ActivityScenario.launch(TvLoginActivity::class.java)
        loginRobot.signIn()
            .waitUntilLoginCodeIsDisplayed()
            .verify { loginCodeViewIsDisplayed() }
    }

    @Test
    fun loginQrSuccess() {
        activityScenario = ActivityScenario.launch(TvQrLoginActivity::class.java)
        TvLoginRobot().waitForQrCode()
        protonHiltRule.mockDispatcher.prependRules {
            rule(get, path eq "/auth/v4/sessions/forks/$FORK_SELECTOR") {
                respond(forkedSessionResponse)
            }
        }

        LegacyTvLoginRobot()
            .waitUntilLoggedIn()
            .verify { userIsLoggedIn() }
    }

    @Test
    fun loginQrFallbackCode() {
        activityScenario = ActivityScenario.launch(TvQrLoginActivity::class.java)
        with(TvLoginRobot()) {
            waitForQrCode()
            navigateToTroubleSigningIn()
            composeRule.mainClock.advanceTimeBy(10 * 60_000) // Skip timeout animation.
            val userCode = getUserCode()
            assertEquals(FORK_USER_CODE, userCode)
        }
    }

    @Test
    fun loginQrNetworkFailureAndRetry() {
        activityScenario = ActivityScenario.launch(TvQrLoginActivity::class.java)
        with(TvLoginRobot()) {
            waitForQrCode()
            protonHiltRule.mockDispatcher.prependRules {
                rule(get, path eq "/auth/v4/sessions/forks/$FORK_SELECTOR") {
                    respond(400, "Test error")
                }
            }
            verify {
                assertErrorNetwork()
            }

            protonHiltRule.mockDispatcher.prependRules {
                rule(get, path eq "/auth/v4/sessions/forks/$FORK_SELECTOR") {
                    respond(422, "Unknown selector")
                }
            }
            clickCreateNewCode()
            waitForQrCode()
        }
    }

    private fun createUserResponse(userId: String) = UserResponse(
        id = userId,
        name = "",
        usedSpace = 0,
        currency = "EUR",
        credit = 0,
        createTimeSeconds = 0,
        maxSpace = 1000,
        maxUpload = 1000,
        type = 0,
        role = 2,
        private = 0,
        subscribed = 5,
        services = 5,
        delinquent = 0,
        email = "a@b.c",
        displayName = "",
        keys = emptyList(),
    )

    private fun createAddressResponse() = AddressResponse(
        id = "address ID",
        domainId = null,
        email = "user1address1@example.com",
        send = 1,
        receive = 1,
        status = 1,
        type = AddressType.Alias.value,
        order = 0,
        displayName = "User1 Address1",
        signature = null,
        hasKeys = 0,
        keys = emptyList()
    )
}
