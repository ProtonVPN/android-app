package com.protonvpn.tests.login.tv

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.android.appconfig.SessionForkSelectorResponse
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.android.tv.login.TvLoginViewModel
import com.protonvpn.mocks.MockUserRepository
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.robots.tv.TvLoginRobot
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createAccountUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testsHelper.UserDataHelper
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import javax.inject.Inject

private const val FORK_SELECTOR = "fork_selector"
private const val FORK_USER_CODE = "1234ABCD"

/**
 * [LoginTestsMocked] Contains all tests related to Login actions.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class LoginTestsMocked {
    // Login tests start with mock API in logged out state.
    private val mockApiConfig = TestApiConfig.Mocked(TestUser.plusUser) {
        rule(get, path eq "/auth/v4/sessions/forks") {
            respond(SessionForkSelectorResponse(FORK_SELECTOR, FORK_USER_CODE))
        }

        rule(get, path eq "/auth/v4/sessions/forks/$FORK_SELECTOR") {
            respond(TvLoginViewModel.HTTP_CODE_KEEP_POLLING)
        }
    }

    private val activityRule = ActivityScenarioRule(TvLoginActivity::class.java)
    private val hiltRule = ProtonHiltAndroidRule(this, mockApiConfig)

    @get:Rule
    val rules = RuleChain.outerRule(hiltRule)
        .around(activityRule)

    @Inject
    lateinit var mockUserRepository: MockUserRepository

    private val loginRobot = TvLoginRobot()
    private lateinit var userDataHelper: UserDataHelper

    @Before
    fun setUp() {
        hiltRule.inject()
        userDataHelper = UserDataHelper()
        runBlocking {
            mockUserRepository.setMockUser(createAccountUser())
        }
    }

    @Test
    fun loginHappyPath() {
        loginRobot
            .signIn()
        hiltRule.mockDispatcher.prependRules {
            rule(get, path eq "/auth/v4/sessions/forks/$FORK_SELECTOR") {
                respond(TestUser.forkedSessionResponse)
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
}