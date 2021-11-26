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
import com.protonvpn.android.ui.home.HomeActivity
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.SetUserPreferencesRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * [AccountRobotTests] contains UI tests for Account view
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class AccountRobotTests {

    private val homeRobot = HomeRobot()
    private val accountRobot = AccountRobot()
    private val testUser = TestUser.plusUser

    private val intent =
        Intent(InstrumentationRegistry.getInstrumentation().targetContext, HomeActivity::class.java)

    @get:Rule
    var rules = RuleChain
        .outerRule(HiltAndroidRule(this))
        .around(SetUserPreferencesRule(TestUser.plusUser))

    @Before
    fun setUp() {
        Intents.init()
        ActivityScenario.launch<HomeActivity>(intent)
        //It blocks outgoing intents and won't open browser for account tests.
        intending(not(isInternal()))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @Test
    fun checkIfUserNameDisplayedInAccountSection() {
        homeRobot.openAccountView()
        accountRobot.verify { checkIfCorrectUsernameIsDisplayed(testUser) }
        accountRobot.clickManageAccount()
            .verify { checkIfAccountButtonHasCorrectUrl() }
    }

    @After
    fun tearDown(){
        Intents.release()
    }
}