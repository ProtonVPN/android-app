package com.protonvpn.tests.account

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.actions.AccountRobot
import com.protonvpn.actions.HomeRobot
import com.protonvpn.android.appconfig.CachedPurchaseEnabled
import com.protonvpn.android.ui.home.HomeActivity
import com.protonvpn.annotations.TestID
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.SetLoggedInUserRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * [AccountTests] contains UI tests for Account view
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class AccountTests {

    private val homeRobot = HomeRobot()
    private val accountRobot = AccountRobot()
    private val testUser = TestUser.plusUser

    private val intent =
        Intent(InstrumentationRegistry.getInstrumentation().targetContext, HomeActivity::class.java)

    private val hiltRule = ProtonHiltAndroidRule(this, TestApiConfig.Mocked(TestUser.plusUser))

    @get:Rule
    var rules = RuleChain
        .outerRule(hiltRule)
        .around(SetLoggedInUserRule(TestUser.plusUser))

    @Inject lateinit var purchaseEnabled: CachedPurchaseEnabled

    @Before
    fun setUp() {
        hiltRule.inject()
        Intents.init()
        ActivityScenario.launch<HomeActivity>(intent)
        //It blocks outgoing intents and won't open browser for account tests.
        intending(not(isInternal()))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @Test
    @TestID(86)
    fun checkIfUserNameDisplayedInAccountSection() {
        homeRobot.openAccountView()
        accountRobot.verify { checkIfCorrectUsernameIsDisplayed(testUser.email) }
        if (purchaseEnabled()) {
            accountRobot.clickManageAccount()
                .verify { checkIfAccountButtonHasCorrectUrl() }
        }
    }

    @Test
    @TestID(121424)
    fun checkHamburgerAccountOptionNavigation(){
        homeRobot.clickOnDrawerMenuAccountOption()
            .verify { checkIfCorrectUsernameIsDisplayed(testUser.email) }
    }

    @Test
    @TestID(121425)
    fun showLogNavigation(){
        homeRobot.clickOnDrawerMenuShowLogOption()
            .verify { assertThatInLogsScreen() }
        homeRobot.scrollUpToTheLogs()
    }

    @Test
    @TestID(121426)
    fun helpOption(){
        homeRobot.clickOnDrawerMenuHelpOption()
            .verify { helpOptionOpensUrl() }
    }

    @After
    fun tearDown(){
        Intents.release()
    }
}
