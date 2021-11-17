package com.protonvpn.tests.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.actions.AccountRobot
import com.protonvpn.actions.HomeRobot
import com.protonvpn.test.shared.TestUser
import com.protonvpn.tests.testRules.ProtonHomeActivityTestRule
import com.protonvpn.tests.testRules.SetUserPreferencesRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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

    @get:Rule
    var rules = RuleChain
            .outerRule(HiltAndroidRule(this))
            .around(SetUserPreferencesRule(TestUser.plusUser))
            .around(ProtonHomeActivityTestRule())

    @Test
    fun checkIfUserNameDisplayedInAccountSection(){
        homeRobot.openAccountView()
        accountRobot.verify { checkIfCorrectUsernameIsDisplayed(testUser) }
        accountRobot.clickManageAccount()
                .verify { checkIfAccountButtonOpensBrowser("com.android.chrome") }
    }
}