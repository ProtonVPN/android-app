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

import android.util.Base64
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.ui.promooffers.PromoOfferActivity
import com.protonvpn.base.BaseVerify
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.ApiNotificationTestHelper.OFFER_ID
import com.protonvpn.test.shared.ApiNotificationTestHelper.PNG_BASE64
import com.protonvpn.test.shared.ApiNotificationTestHelper.createNotificationJsonWithPanel
import com.protonvpn.test.shared.ApiNotificationTestHelper.createNotificationsResponseJson
import com.protonvpn.testRules.ProtonHiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class PromoOfferActivityTests {

    @get:Rule
    var hiltRule = ProtonHiltAndroidRule(this, TestApiConfig.Mocked())

    @Inject lateinit var apiNotificationManager: ApiNotificationManager

    private val verify = BaseVerify()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun titleAndButtonOnly() {
        val panel = """
                "Title": "Notification title",
                "Button": {
                  "Text": "Upgrade",
                  "URL": "https://proton.me"
                }
            """.trimIndent()
        launchOfferActivityWithPanel(panel)
        verify.checkIfElementByIdContainsText(R.id.textTitle, "Notification title")
        verify.checkIfElementByIdContainsText(R.id.buttonOpenOffer, "Upgrade")
        // Nothing else is displayed
        verify.checkIfElementIsNotDisplayedById(R.id.textIncentive)
        verify.checkIfElementIsNotDisplayedById(R.id.textPill)
        verify.checkIfElementIsNotDisplayedById(R.id.imagePicture)
        verify.checkIfElementIsNotDisplayedById(R.id.layoutFeatures)
        verify.checkIfElementIsNotDisplayedById(R.id.imageFullScreen)
        verify.checkIfElementIsNotDisplayedById(R.id.textFooter)
    }

    @Test
    fun fieldsWithEmptyTextAreNotShown() {
        val panel = """
            "Title": "",
            "Incentive": "",
            "Pill": "",
            "PictureURL": "",
            "FeaturesFooter": "",
            "PageFooter": ""
        """.trimIndent()
        launchOfferActivityWithPanel(panel)
        verify.checkIfElementIsNotDisplayedById(R.id.textTitle)
        verify.checkIfElementIsNotDisplayedById(R.id.textIncentive)
        verify.checkIfElementIsNotDisplayedById(R.id.textPill)
        verify.checkIfElementIsNotDisplayedById(R.id.imagePicture)
        verify.checkIfElementIsNotDisplayedById(R.id.layoutFeatures)
        verify.checkIfElementIsNotDisplayedById(R.id.imageFullScreen)
        verify.checkIfElementIsNotDisplayedById(R.id.textFooter)
    }

    @Test
    fun incentiveTextWithoutPrice() {
        val panelJson = """
            "Incentive": "Incentive text"
        """
        launchOfferActivityWithPanel(panelJson)
        verify.checkIfElementByIdContainsText(R.id.textIncentive, "Incentive text")
    }

    @Test
    fun incentiveTextWithPrice() {
        val panelJson = """
            "Incentive": "Incentive %IncentivePrice%",
            "IncentivePrice": "$1.99"
        """
        launchOfferActivityWithPanel(panelJson)
        verify.checkIfElementByIdContainsText(R.id.textIncentive, "Incentive $1.99")
    }

    @Test
    fun incentiveTextWithTokenButNoPriceDoesntCrash() {
        val panelJson = """
            "Incentive": "Incentive %IncentivePrice%"
        """
        launchOfferActivityWithPanel(panelJson)
        verify.checkIfElementByIdContainsText(R.id.textIncentive, "Incentive %IncentivePrice%")
    }

    @Test
    fun emptyFeaturesList() {
        val panelJson = """
            "Features": []
        """.trimIndent()
        launchOfferActivityWithPanel(panelJson)
        verify.checkIfElementIsNotDisplayedById(R.id.layoutFeatures)
    }

    @Test
    fun featuresFooterWithNoFeatures() {
        val panelJson = """
            "FeaturesFooter": "Footer text"
        """.trimIndent()
        launchOfferActivityWithPanel(panelJson)
        verify.checkIfElementIsDisplayedByText("Footer text")
    }

    @Test
    fun lottieIsSupportedInPicture() {
        val lottieBase64 = loadLottieBase64()
        val panelJson = """
            "PictureURL": "data:image;base64,$lottieBase64"
        """.trimIndent()
        launchOfferActivityWithPanel(panelJson)
        verify.checkIfElementIsDisplayedById(R.id.imagePicture)
    }

    @Test
    fun lottieIsSupportedInFullScreenImage() {
        val lottieBase64 = loadLottieBase64()
        val panelJson = """
            "FullScreenImage": {
              "Source": [
                {
                  "Type": "LOTTIE",
                  "URL": "data:image;base64,$lottieBase64"
                }
              ]
            }
        """.trimIndent()
        launchOfferActivityWithPanel(panelJson)
        verify.checkIfElementIsDisplayedById(R.id.imageFullScreen)
    }

    @Test
    fun fullScreenImageAndButton() {
        val panelJson = """
            "FullScreenImage": {
              "Source": [
                {
                  "Type": "PNG",
                  "URL": "data:image/png;base64,$PNG_BASE64",
                  "Width": 1
                }
              ],
              "AlternativeText": "Image description"
            },
            "Button": {
              "Text": "Upgrade",
              "URL": "https://proton.me"
            }
        """.trimIndent()
        launchOfferActivityWithPanel(panelJson)
        verify.checkIfElementIsDisplayedById(R.id.imageFullScreen)
        verify.checkIfElementIsDisplayedByContentDesc("Image description")
    }

    @Test
    fun fullScreenImageOverridesOtherElements() {
        val panelJson = """
            "Title": "Title",
            "FullScreenImage": {
              "Source": [
                {
                  "Type": "PNG",
                  "URL": "data:image/png;base64,$PNG_BASE64",
                  "Width": 1
                }
              ],
              "AlternativeText": "Image description"
            },
            "Button": {
              "Text": "Upgrade",
              "URL": "https://proton.me"
            }
        """.trimIndent()
        launchOfferActivityWithPanel(panelJson)
        verify.checkIfElementIsNotDisplayedById(R.id.textTitle)
        verify.checkIfElementIsDisplayedById(R.id.imageFullScreen)
        verify.checkIfElementIsDisplayedByContentDesc("Image description")
    }

    @Test
    fun fullScreenAlternativeTextWhenImageNotSpecified() {
        val panelJson = """
            "FullScreenImage": {
              "AlternativeText": "Image description"
            }
        """
        launchOfferActivityWithPanel(panelJson)
        verify.checkIfElementIsDisplayedByText("Image description")
    }

    @Test
    fun fullScreenOneTimeNotification() {
        val panelJson = """
            "FullScreenImage": {
              "Source": [
                {
                  "Type": "PNG",
                  "URL": "data:image/png;base64,$PNG_BASE64",
                  "Width": 1
                }
              ]
            },
            "Button": {
              "Text": "Upgrade",
              "URL": "https://proton.me"
            }
        """.trimIndent()
        launchOfferActivityWithPanel(panelJson, ApiNotificationTypes.TYPE_ONE_TIME_POPUP)
        verify.checkIfElementIsDisplayedById(R.id.imageFullScreen)
        verify.checkIfElementIsDisplayedById(R.id.buttonOpenOffer)
    }

    private fun launchOfferActivityWithPanel(panelJson: String, type: Int = ApiNotificationTypes.TYPE_TOOLBAR) {
        val notificationJson = createNotificationJsonWithPanel(panelJson, type)
        apiNotificationManager.setTestNotificationsResponseJson(createNotificationsResponseJson(notificationJson))
        runBlocking {
            // Wait for the notification to be processed and emitted before opening the activity.
            withTimeout(1_000) {
                apiNotificationManager.activeListFlow.first { notifications -> notifications.isNotEmpty() }
            }
        }
        val intent =
            PromoOfferActivity.createIntent(InstrumentationRegistry.getInstrumentation().targetContext, OFFER_ID)
        ActivityScenario.launch<PromoOfferActivity>(intent)
    }

    private fun loadLottieBase64(): String {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        return resources.openRawResource(R.raw.vpn_pulsing_logo).bufferedReader().use { reader ->
            val bytes = reader.readText().toByteArray()
            String(Base64.encode(bytes, 0))
        }
    }
}
