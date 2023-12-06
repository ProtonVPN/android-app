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

package com.protonvpn.tests.partnership

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.actions.HomeRobot
import com.protonvpn.android.models.vpn.PartnersResponse
import com.protonvpn.android.models.vpn.ServerList
import com.protonvpn.android.ui.home.HomeActivity
import com.protonvpn.data.PartnersData.multiplePartnerServers
import com.protonvpn.data.PartnersData.newsPartner
import com.protonvpn.data.PartnersData.newsPartner2
import com.protonvpn.data.PartnersData.newsPartnerType
import com.protonvpn.data.PartnersData.newsPartnerType2
import com.protonvpn.data.PartnersData.partnerServerNews
import com.protonvpn.data.PartnersData.partnerServerNews2
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testsHelper.TestSetup
import com.protonvpn.testsHelper.UserDataHelper
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.fail

@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class PartnershipTests {

    private val partnerCountry = "Poland"
    private lateinit var homeRobot: HomeRobot

    private val mockApiConfig = TestApiConfig.Mocked(TestUser.freeUser) {
        rule(get, path eq "/vpn/v1/partners") {
            respond(PartnersResponse(listOf(newsPartnerType)))
        }
        rule(get, path eq "/vpn/v1/logicals") {
            respond(ServerList(listOf(partnerServerNews)))
        }
    }

    @get:Rule
    val hiltRule = ProtonHiltAndroidRule(this, mockApiConfig)

    @Before
    fun setUp() {
        homeRobot = HomeRobot()
    }

    @Test
    fun partnerInfoScreenWhenClickingOnInfoIcon() {
        startApplication()
        ActivityScenario.launch(HomeActivity::class.java).use {
            homeRobot.openPartnersInfo(partnerCountry)
                .verify {
                    checkIfPartnersDataIsDisplayedProperly(newsPartner)
                    checkIfPartnerTypeIsShown(newsPartnerType)
                }
        }
    }

    @Test
    fun partnerInfoScreenWhenClickingOnPartnerLogo() {
        startApplication()
        ActivityScenario.launch(HomeActivity::class.java).use {
            homeRobot.openPartnersInfoUsingLogo(partnerCountry, partnerServerNews.serverName, newsPartner.name!!)
                .verify {
                    checkIfPartnersDataIsDisplayedProperly(newsPartner)
                    checkIfPartnerTypeIsShown(newsPartnerType)
                }
        }
    }

    @Test
    fun onePartnerIsShownForMultiplePartnerServers() {
        hiltRule.mockDispatcher.prependRules {
            rule(get, path eq "/vpn/v1/logicals") {
                respond(ServerList(multiplePartnerServers))
            }
        }

        startApplication()
        ActivityScenario.launch(HomeActivity::class.java).use {
            homeRobot.openPartnersInfo(partnerCountry)
                .verify {
                    checkIfPartnersDataIsDisplayedProperly(newsPartner)
                    checkIfPartnerTypeIsShown(newsPartnerType)
                    checkIfPartnerIsNotDisplayed(newsPartner2)
                }
        }
    }

    @Test
    fun twoPartnersAreShownForSingleMultiPartnerServer() {
        hiltRule.mockDispatcher.prependRules {
            rule(get, path eq "/vpn/v1/partners") {
                respond(PartnersResponse(listOf(newsPartnerType, newsPartnerType2)))
            }
            rule(get, path eq "/vpn/v1/logicals") {
                respond(ServerList(listOf(partnerServerNews2)))
            }
        }

        startApplication()
        ActivityScenario.launch(HomeActivity::class.java).use {
            homeRobot.openPartnersInfo(partnerCountry)
                .verify {
                    checkIfPartnersDataIsDisplayedProperly(newsPartner)
                    checkIfPartnersDataIsDisplayedProperly(newsPartner2)
                    checkIfPartnerTypeIsShown(newsPartnerType)
                    checkIfPartnerTypeIsShown(newsPartnerType2)
                }
        }
    }

    @Test
    fun onePartnerIsShownWhenSiblingServerHas2Partners() {
        hiltRule.mockDispatcher.prependRules {
            rule(get, path eq "/vpn/v1/partners") {
                respond(PartnersResponse(listOf(newsPartnerType, newsPartnerType2)))
            }
            rule(get, path eq "/vpn/v1/logicals") {
                respond(ServerList(multiplePartnerServers))
            }
        }

        startApplication()
        ActivityScenario.launch(HomeActivity::class.java).use {
            homeRobot.openPartnersInfoUsingLogo(partnerCountry, partnerServerNews.serverName, newsPartner.name!!)
                .verify {
                    checkIfPartnersDataIsDisplayedProperly(newsPartner)
                    checkIfPartnerTypeIsShown(newsPartnerType)
                    checkIfPartnerIsNotDisplayed(newsPartner2)
                }
        }
    }

    @Test
    fun partnersNotCalledWhenNoPartnerServers() {
        hiltRule.mockDispatcher.prependRules {
            rule(get, path eq "/vpn/v1/partners") {
                fail("Partners endpoint shouldn't be called when there are no partnerships")
            }
            rule(get, path eq "/vpn/v1/logicals") {
                respond(MockedServers.serverList)
            }
        }
        startApplication()
        Espresso.onIdle()
    }

    private fun startApplication() {
        hiltRule.startApplicationAndWaitForIdle()
        TestSetup.setCompletedOnboarding()
        UserDataHelper().setUserData(TestUser.freeUser)
    }
}
