/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.tests.promooffer

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasCategories
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.ui.home.HomeActivity
import com.protonvpn.android.ui.promooffers.PromoOfferActivity
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.testRules.ProtonHiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.any
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

private const val OFFER_ID = "offer ID" // TODO: move to common ApiNotificationTestHelper?

@HiltAndroidTest
class HomeActivityPromoOfferTests {

    @get:Rule
    var hiltRule = ProtonHiltAndroidRule(this)

    @Inject
    lateinit var apiNotificationManager: ApiNotificationManager

    private val verify = BaseVerify()
    private val robot = BaseRobot()

    @Before
    fun setup() {
        hiltRule.inject()
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun toolbarNotificationWithNoPanel() {
        val json = createNotificationJsonWithOffer(
            ApiNotificationTypes.TYPE_OFFER, """
                {
                  "URL": "https://proton.me"
                }
            """.trimIndent()
        )
        launchHomeActivityWithNotification(json)
        verify.checkIfElementIsDisplayedById(R.id.imageNotification)

        // Don't open any activities.
        Intents.intending(any(Intent::class.java))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        robot.clickElementById<BaseRobot>(R.id.imageNotification)
        Intents.intended(allOf(
            hasCategories(setOf(Intent.CATEGORY_BROWSABLE)),
            hasAction(Intent.ACTION_VIEW),
            hasData("https://proton.me")
        ))
    }

    @Test
    fun toolbarNotificationWithPanel() {
        val json = createNotificationJsonWithOffer(
            ApiNotificationTypes.TYPE_OFFER, """
                {
                  "Panel": {}
                }
            """.trimIndent()
        )
        launchHomeActivityWithNotification(json)
        verify.checkIfElementIsDisplayedById(R.id.imageNotification)

        // Don't open any activities.
        Intents.intending(any(Intent::class.java))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        robot.clickElementById<BaseRobot>(R.id.imageNotification)
        Intents.intended(hasComponent(
            ComponentName(InstrumentationRegistry.getInstrumentation().targetContext, PromoOfferActivity::class.java))
        )
    }

    @Test
    fun toolbarNotificationWithUrlAndPanelOpensPanel() {
        val json = createNotificationJsonWithOffer(
            ApiNotificationTypes.TYPE_OFFER, """
                {
                  "URL": "https://proton.me",
                  "Panel": {}
                }
            """.trimIndent()
        )
        launchHomeActivityWithNotification(json)
        verify.checkIfElementIsDisplayedById(R.id.imageNotification)

        // Don't open any activities.
        Intents.intending(any(Intent::class.java))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        robot.clickElementById<BaseRobot>(R.id.imageNotification)
        Intents.intended(hasComponent(
            ComponentName(InstrumentationRegistry.getInstrumentation().targetContext, PromoOfferActivity::class.java))
        )
    }

    @Test
    fun noToolbarNotificationWhenBothUrlAndPanelAreMissing() {
        val json = createNotificationJsonWithOffer(ApiNotificationTypes.TYPE_OFFER, "{}")
        launchHomeActivityWithNotification(json)
        verify.checkIfElementIsNotDisplayedById(R.id.imageNotification)
    }

    private fun createNotificationJsonWithOffer(type: Int, offerJson: String): String = """
        {
          "NotificationID": "$OFFER_ID",
          "StartTime": 0,
          "EndTime": ${Integer.MAX_VALUE},
          "Type": $type,
          "Offer": $offerJson
        }
    """.trimIndent()

    private fun launchHomeActivityWithNotification(notificationJson: String) {
        apiNotificationManager.setTestNotificationJson(notificationJson)
        val intent = Intent(InstrumentationRegistry.getInstrumentation().targetContext, HomeActivity::class.java)
        ActivityScenario.launch<HomeActivity>(intent)
    }
}
