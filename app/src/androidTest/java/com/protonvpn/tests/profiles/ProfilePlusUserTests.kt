/*
 *  Copyright (c) 2021 Proton AG
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

package com.protonvpn.tests.profiles

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.actions.ConnectionRobot
import com.protonvpn.actions.HomeRobot
import com.protonvpn.actions.ProfilesRobot
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.data.DefaultData
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.ProtonHomeActivityTestRule
import com.protonvpn.testRules.SetLoggedInUserRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * [ProfilePlusUserTests] contains tests related to profile actions
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ProfilePlusUserTests {

    private val testRule = ProtonHomeActivityTestRule()
    private val newProfileName = "New test profile"

    @get:Rule
    var rules = RuleChain
        .outerRule(ProtonHiltAndroidRule(this, TestApiConfig.Mocked(TestUser.plusUser)))
        .around(SetLoggedInUserRule(TestUser.plusUser))
        .around(testRule)

    private lateinit var homeRobot: HomeRobot
    private lateinit var profilesRobot: ProfilesRobot
    private lateinit var connectionRobot: ConnectionRobot

    @Before
    fun setup() {
        homeRobot = HomeRobot()
        profilesRobot = ProfilesRobot()
        connectionRobot = ConnectionRobot()
    }

    @Test
    fun defaultProfileOptions() {
        homeRobot.swipeLeftToOpenProfiles()
            .verify { defaultProfileOptionsAreVisible() }
    }

    @Test
    fun tryToCreateProfileWithoutName() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .insertTextInProfileNameField("")
            .clickOnSaveButton()
            .verify { errorEmptyNameIsVisible() }
    }

    @Test
    fun tryToCreateProfileWithoutCountry() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .clickOnSaveButton()
            .verify { errorEmptyCountryIsVisible() }
    }

    @Test
    fun testServerPreselection() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .selectFirstCountry()
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(DefaultData.PROFILE_NAME)
            }
    }

    @Test
    fun tryToCreateProfile() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .selectFirstCountry()
            .selectRandomServer()
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(DefaultData.PROFILE_NAME)
            }
    }

    @Test
    fun tryToCreateProfileWithSelectedColors() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .selectColorIndex(2)
            .selectFirstCountry()
            .selectRandomServer()
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(DefaultData.PROFILE_NAME)
            }
    }

    @Test
    fun tryToConnectToProfile() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.swipeLeftToOpenProfiles()
            .clickOnCreateNewProfileButton()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .selectFirstCountry()
            .selectRandomServer()
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(DefaultData.PROFILE_NAME)
            }
        profilesRobot.clickOnConnectButtonUntilConnected(DefaultData.PROFILE_NAME)
            .verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    fun discardNewProfile() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .selectFirstCountry()
            .selectRandomServer()
            .navigateBackFromForm()
            .verify { discardChangesDialogIsVisible() }
        profilesRobot.clickDiscardButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsNotVisible(DefaultData.PROFILE_NAME)
            }
    }

    @Test
    fun cancelDiscardActionAndSaveProfile() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .selectFirstCountry()
            .selectRandomServer()
            .navigateBackFromForm()
            .verify { discardChangesDialogIsVisible() }
        profilesRobot.clickCancelButton()
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(DefaultData.PROFILE_NAME)
            }
    }

    @Test
    fun tryToSaveSecureCoreProfileWithoutExitCountry() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .enableSecureCore()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .selectSecondSecureCoreExitCountry()
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(DefaultData.PROFILE_NAME)
            }
    }

    @Test
    fun saveSecureCoreProfile() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .enableSecureCore()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .selectSecondSecureCoreExitCountry()
            .selectSecureCoreEntryCountryForSecondExit()
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(DefaultData.PROFILE_NAME)
            }
    }

    @Test
    fun connectToCreatedSecureCoreProfile() {
        testRule.mockStatusOnConnect(VpnState.Connected)
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .enableSecureCore()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .selectSecondSecureCoreExitCountry()
            .selectSecureCoreEntryCountryForSecondExit()
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(DefaultData.PROFILE_NAME)
            }
        profilesRobot.clickOnConnectButton(DefaultData.PROFILE_NAME)
        connectionRobot
            .verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    fun editSecureCoreProfile() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .enableSecureCore()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .selectSecondSecureCoreExitCountry()
            .selectSecureCoreEntryCountryForSecondExit()
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(DefaultData.PROFILE_NAME)
            }
        profilesRobot.clickEditProfile()
            .updateProfileName(newProfileName)
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(newProfileName)
            }
    }

    @Test
    fun deleteSecureCoreProfile() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .enableSecureCore()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .selectSecondSecureCoreExitCountry()
            .selectSecureCoreEntryCountryForSecondExit()
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(DefaultData.PROFILE_NAME)
            }
        profilesRobot.clickEditProfile()
            .clickDeleteProfile()
            .verify { profileIsNotVisible(DefaultData.PROFILE_NAME) }
    }

    @Test
    fun editProfile() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .selectFirstCountry()
            .selectRandomServer()
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(DefaultData.PROFILE_NAME)
            }
        profilesRobot.clickEditProfile()
            .updateProfileName(newProfileName)
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(newProfileName)
            }
    }

    @Test
    fun deleteProfile() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .selectFirstCountry()
            .selectRandomServer()
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(DefaultData.PROFILE_NAME)
            }
        profilesRobot.clickEditProfile()
            .clickDeleteProfile()
            .verify { profileIsNotVisible(DefaultData.PROFILE_NAME) }
    }

    @Test
    fun createProfileWithOpenVPNTCPProtocol() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .selectFirstCountry()
            .selectRandomServer()
            .selectOpenVPNProtocol(udp = false)
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(DefaultData.PROFILE_NAME)
            }
    }

    @Test
    fun createProfileWithOpenVPNUDPProtocol() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .selectFirstCountry()
            .selectRandomServer()
            .selectOpenVPNProtocol(udp = true)
            .clickOnSaveButton()
            .verify {
                defaultProfileOptionsAreVisible()
                profileIsVisible(DefaultData.PROFILE_NAME)
            }
    }
}
