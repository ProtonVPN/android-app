package com.protonvpn.tests.accountrecovery

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.actions.compose.HomeRobot
import com.protonvpn.actions.compose.interfaces.verify
import com.protonvpn.android.ui.onboarding.SplashActivity
import com.protonvpn.mocks.FakeIsAccountRecoveryEnabled
import com.protonvpn.mocks.FakeIsNotificationsEnabled
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.ProtonHiltInjectRule
import com.protonvpn.testRules.ProtonPermissionsRule
import com.protonvpn.testsHelper.TestSetup
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.core.accountmanager.data.AccountStateHandler
import me.proton.core.accountrecovery.test.MinimalAccountRecoveryNotificationTest
import me.proton.core.auth.test.usecase.WaitForPrimaryAccount
import me.proton.core.eventmanager.domain.EventManagerProvider
import me.proton.core.eventmanager.domain.repository.EventMetadataRepository
import me.proton.core.network.data.ApiProvider
import me.proton.core.notification.domain.repository.NotificationRepository
import me.proton.core.test.quark.Quark
import me.proton.test.fusion.FusionConfig
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import javax.inject.Inject

@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class AccountRecoveryNotificationTest : MinimalAccountRecoveryNotificationTest {
    @get:Rule(order = 0)
    val permissionsRule = ProtonPermissionsRule()

    @get:Rule(order = 1)
    val hiltRule = ProtonHiltAndroidRule(this, TestApiConfig.Backend)

    @get:Rule(order = 2)
    val injectRule = ProtonHiltInjectRule(hiltRule)

    @get:Rule(order = 3)
    val initFeaturesRule = object : ExternalResource() {
        override fun before() {
            isAccountRecoveryEnabled.enabled = true
            isNotificationsEnabled.enabled = true
        }
    }

    @get:Rule(order = 4)
    val composeTestRule: ComposeTestRule = createAndroidComposeRule<SplashActivity>().apply {
        FusionConfig.Compose.testRule.set(this)
    }

    @Inject
    lateinit var isAccountRecoveryEnabled: FakeIsAccountRecoveryEnabled

    @Inject
    lateinit var isNotificationsEnabled: FakeIsNotificationsEnabled

    @Inject
    override lateinit var accountStateHandler: AccountStateHandler

    @Inject
    override lateinit var apiProvider: ApiProvider

    @Inject
    override lateinit var eventManagerProvider: EventManagerProvider

    @Inject
    override lateinit var eventMetadataRepository: EventMetadataRepository

    @Inject
    override lateinit var notificationRepository: NotificationRepository

    @Inject
    override lateinit var waitForPrimaryAccount: WaitForPrimaryAccount

    override val quark: Quark = TestSetup.quark

    @Before
    fun setUp() {
        TestSetup.setCompletedOnboarding()
    }

    override fun verifyAfterLogin() {
        HomeRobot.verify { isLoggedIn() }
    }
}
