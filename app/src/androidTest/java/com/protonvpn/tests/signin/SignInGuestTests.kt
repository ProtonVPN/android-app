package com.protonvpn.tests.signin

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.protonvpn.actions.HumanVerificationRobot
import com.protonvpn.actions.OnboardingRobot
import com.protonvpn.actions.SignupRobot
import com.protonvpn.actions.compose.HomeRobot
import com.protonvpn.actions.compose.SettingsRobot
import com.protonvpn.actions.compose.interfaces.verify
import com.protonvpn.android.redesign.app.ui.MainActivity
import com.protonvpn.testRules.CommonRuleChains.realBackendRule
import com.protonvpn.testsHelper.TestSetup
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.core.auth.test.MinimalSignInGuestTests
import me.proton.core.auth.test.fake.FakeIsCredentialLessEnabled
import me.proton.core.auth.test.robot.CredentialLessWelcomeRobot
import me.proton.core.auth.test.robot.signup.SetPasswordRobot
import me.proton.core.auth.test.robot.signup.SignUpRobot
import me.proton.core.test.quark.Quark
import me.proton.core.util.kotlin.random
import me.proton.test.fusion.FusionConfig
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import javax.inject.Inject

@LargeTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28) // HV robot doesn't work well on older Android versions
@HiltAndroidTest
class SignInGuestTests : MinimalSignInGuestTests {

    private val humanVerificationRobot = HumanVerificationRobot()

    @get:Rule
    val rule: RuleChain = realBackendRule()
        .around(object : ExternalResource() {
            override fun before() {
                isCredentialLessEnabled.localEnabled = true
                isCredentialLessEnabled.remoteDisabled = suspend { false }
            }
        })
        .around(createAndroidComposeRule<MainActivity>().also {
            FusionConfig.Compose.testRule.set(it)
        })

    @Inject
    lateinit var isCredentialLessEnabled: FakeIsCredentialLessEnabled

    override val quark: Quark get() = TestSetup.quark

    @Test
    override fun credentialLessToRegularAccount() {
        CredentialLessWelcomeRobot.clickContinueAsGuest()
        verifyAfterCredentialLessSignup()

        navigateToSignupFromCredentialLess()

        val testUsername = "test-${String.random()}"
        SignUpRobot.forExternal().clickSwitch()
        SignUpRobot
            .forInternal()
            .fillUsername(testUsername)
            .clickNext()
        SetPasswordRobot
            .fillAndClickNext("123123123")

        SignupRobot().enterRecoveryEmail("${testUsername}@proton.ch")
        humanVerificationRobot.verifyViaEmail()

        verifyAfterRegularSignup(testUsername)
    }

    override fun navigateToSignupFromCredentialLess() {
        HomeRobot
            .navigateToSettings()
            .createAccount()
    }

    override fun verifyAfterCredentialLessSignup() {
        OnboardingRobot()
            .apply { verify { onboardingPaymentIdDisplayed() } }
            .skipOnboardingPayment()
            .apply { verify { isHomeDisplayed() } }
        HomeRobot.verify { isLoggedIn() }
    }

    override fun verifyAfterRegularSignup(username: String) {
        OnboardingRobot()
            .apply { verify { onboardingPaymentIdDisplayed() } }
            .skipOnboardingPayment()
        SettingsRobot
            .verify { usernameIsDisplayed(username) }
    }
}
