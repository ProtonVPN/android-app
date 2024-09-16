package com.protonvpn.tests.accountrecovery

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.actions.compose.HomeRobot
import com.protonvpn.actions.compose.interfaces.verify
import com.protonvpn.android.redesign.app.ui.MainActivity
import com.protonvpn.testRules.CommonRuleChains.realBackendRule
import com.protonvpn.testRules.ProtonPermissionsRule
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.core.accountmanager.data.AccountStateHandler
import me.proton.core.accountrecovery.test.MinimalAccountRecoveryNotificationTest
import me.proton.core.accountrecovery.test.fake.FakeIsAccountRecoveryEnabled
import me.proton.core.auth.test.usecase.WaitForPrimaryAccount
import me.proton.core.eventmanager.domain.EventManagerProvider
import me.proton.core.eventmanager.domain.repository.EventMetadataRepository
import me.proton.core.network.data.ApiProvider
import me.proton.core.notification.domain.repository.NotificationRepository
import me.proton.core.notification.test.fake.FakeIsNotificationsEnabled
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import javax.inject.Inject

@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class AccountRecoveryNotificationTest : MinimalAccountRecoveryNotificationTest {

    private val initFeaturesRule = object : ExternalResource() {
        override fun before() {
            isAccountRecoveryEnabled.enabled = true
            isNotificationsEnabled.enabled = true
        }
    }

    @get:Rule
    val composeTestRule: RuleChain = realBackendRule()
        .around(ProtonPermissionsRule())
        .around(initFeaturesRule)
        .around(createAndroidComposeRule<MainActivity>())

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

    override fun verifyAfterLogin() {
        HomeRobot.verify { isLoggedIn() }
    }
}
