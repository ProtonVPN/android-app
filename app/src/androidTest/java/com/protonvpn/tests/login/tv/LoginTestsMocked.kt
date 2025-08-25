package com.protonvpn.tests.login.tv

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.android.appconfig.ForkedSessionResponse
import com.protonvpn.android.appconfig.SessionForkSelectorResponse
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.android.tv.login.TvLoginViewModel
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.robots.tv.TvLoginRobot
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testsHelper.UserDataHelper
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.core.key.data.api.response.UserResponse
import me.proton.core.key.data.api.response.UsersResponse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

private const val FORK_SELECTOR = "fork_selector"
private const val FORK_USER_CODE = "1234ABCD"

/**
 * [LoginTestsMocked] Contains all tests related to Login actions.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class LoginTestsMocked {

    private val user = TestUser.plusUser

    val forkedSessionResponse = ForkedSessionResponse(
        864000,
        "Bearer",
        "UId",
        "refreshToken",
        "null",
        0,
        arrayOf("self", "user", "loggedin", "vpn"),
        user.vpnUser.userId.id,
    )

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
    }

    private val activityRule = ActivityScenarioRule(TvLoginActivity::class.java)
    private val hiltRule = ProtonHiltAndroidRule(this, mockApiConfig)

    @get:Rule
    val rules = RuleChain.outerRule(hiltRule)
        .around(activityRule)

    private val loginRobot = TvLoginRobot()
    private lateinit var userDataHelper: UserDataHelper

    @Before
    fun setUp() {
        hiltRule.inject()
        userDataHelper = UserDataHelper()
    }

    @Test
    fun loginHappyPath() {
        loginRobot
            .signIn()
        hiltRule.mockDispatcher.prependRules {
            rule(get, path eq "/auth/v4/sessions/forks/$FORK_SELECTOR") {
                respond(forkedSessionResponse)
            }
        }
        loginRobot
            .waitUntilLoggedIn()
            .verify { userIsLoggedIn() }
    }

    @Test
    fun loginCodeIsDisplayed() {
        loginRobot.signIn()
            .waitUntilLoginCodeIsDisplayed()
            .verify { loginCodeViewIsDisplayed() }
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
}
