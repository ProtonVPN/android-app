/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.tests.freeRescope

import com.protonvpn.actions.HomeRobot
import com.protonvpn.actions.MapRobot
import com.protonvpn.actions.ProfilesRobot
import com.protonvpn.actions.UpsellModalRobot
import com.protonvpn.android.appconfig.AppConfigResponse
import com.protonvpn.android.appconfig.DefaultPorts
import com.protonvpn.android.appconfig.DefaultPortsConfig
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.appconfig.RatingConfig
import com.protonvpn.android.appconfig.SmartProtocolConfig
import com.protonvpn.data.DefaultData
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.CommonRuleChains.mockedLoggedInRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class FreeRescopeTests {

    private val featureFlags = FeatureFlags(showNewFreePlan = true)
    private val ratingConfig = RatingConfig(emptyList(), 0, 0, 0, 0)
    private val smartProtocolConfig =
        SmartProtocolConfig(openVPNEnabled = true, wireguardEnabled = true)
    private val defaultPortsConfig = DefaultPortsConfig(
        DefaultPorts(listOf(443)), DefaultPorts(
            listOf(443)
        )
    )
    private val clientConfig = AppConfigResponse(
        defaultPortsConfig = defaultPortsConfig, featureFlags = featureFlags,
        ratingConfig = ratingConfig, smartProtocolConfig = smartProtocolConfig
    )

    private val mockApiConfig = TestApiConfig.Mocked(TestUser.freeUser) {
        rule(get, path eq "/vpn/v2/clientconfig") {
            respond(clientConfig)
        }
    }

    private lateinit var homeRobot: HomeRobot
    private lateinit var mapRobot: MapRobot
    private lateinit var upsellModalRobot: UpsellModalRobot
    private lateinit var profilesRobot: ProfilesRobot

    @get:Rule
    val rule = mockedLoggedInRule(
        testUser = TestUser.freeUser,
        mockedBackend = mockApiConfig
    )

    @Before
    fun setUp() {
        homeRobot = HomeRobot()
        mapRobot = MapRobot()
        upsellModalRobot = UpsellModalRobot()
        profilesRobot = ProfilesRobot()
        homeRobot.pressGotIt()
    }

    @Test
    fun changeServer() {
        homeRobot.connectViaFastest()
            .verify { changeServerAndDisconnectIsDisplayed() }
        homeRobot.changeServer()
            .verify { changeServerTimerAndBannerAreDisplayed() }
        homeRobot.changeServer()
            .verify { cooldownUpgradeIsShown() }
    }

    @Test
    fun profilesUpgradeModal() {
        homeRobot.swipeLeftToOpenProfiles()
            .verify { defaultProfilesArePaid() }
        profilesRobot.clickOnCreateNewProfileUntilUpsellIsShown()
        upsellModalRobot.verify { profilesUpsellIsShown() }
    }

    @Test
    fun mapUpgradeModal() {
        homeRobot.swipeLeftToOpenMap()
        mapRobot.clickOnCountryNode(DefaultData.TEST_COUNTRY)
            .verify { upgradeButtonIsVisible() }
        mapRobot.clickUpgrade()
        upsellModalRobot.verify { specificCountryUpsellIsShown() }
    }

    @Test
    fun serverListUpgradeModal() {
        homeRobot.clickPlusLocationUpsell()
        upsellModalRobot.verify { countryUpsellIsShown() }
        upsellModalRobot.closeModal()
        homeRobot.clickUpgradeByCountryName(MockedServers.serverList[0].displayName)
        upsellModalRobot.verify { specificCountryUpsellIsShown() }
    }
}
