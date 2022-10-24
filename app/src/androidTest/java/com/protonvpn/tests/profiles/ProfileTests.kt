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
import com.protonvpn.annotations.TestID
import com.protonvpn.data.DefaultData
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.LoginTestRule
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.ProtonHomeActivityTestRule
import com.protonvpn.testsHelper.UserDataHelper
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * [ProfileTests] contains tests related to profile actions
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ProfileTests {

    private val testRule = ProtonHomeActivityTestRule()
    private val newProfileName = "New test profile"

    @get:Rule
    var rules = RuleChain
        .outerRule(ProtonHiltAndroidRule(this))
        .around(LoginTestRule(TestUser.plusUser))
        .around(testRule)

    private lateinit var homeRobot: HomeRobot
    private lateinit var profilesRobot: ProfilesRobot
    private lateinit var connectionRobot: ConnectionRobot
    private lateinit var userDataHelper: UserDataHelper

    @Before
    fun setup() {
        homeRobot = HomeRobot()
        profilesRobot = ProfilesRobot()
        connectionRobot = ConnectionRobot()
        userDataHelper = UserDataHelper()
    }

    @Test
    @TestID(69)
    fun defaultProfileOptions() {
        homeRobot.swipeLeftToOpenProfiles()
            .verify { defaultProfileOptionsAreVisible() }
    }

    @Test
    @TestID(103974)
    fun tryToCreateProfileWithoutName() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .insertTextInProfileNameField("")
            .clickOnSaveButton()
            .verify { errorEmptyNameIsVisible() }
    }

    @Test
    @TestID(103975)
    fun tryToCreateProfileWithoutCountry() {
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .clickOnSaveButton()
            .verify { errorEmptyCountryIsVisible() }
    }

    @Test
    @TestID(103976)
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
    @TestID(70)
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
    @TestID(103977)
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
    @TestID(103978)
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
    @TestID(103979)
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
    @TestID(103981)
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
    @TestID(103983)
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
    @TestID(71)
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
    @TestID(84)
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
        connectionRobot.verify { isConnected() }
        connectionRobot.disconnectFromVPN()
            .verify { isDisconnected() }
    }

    @Test
    @TestID(103982)
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
    @TestID(103984)
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
    @TestID(73)
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
    @TestID(74)
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
    @TestID(103986)
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
    @TestID(103985)
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

    @Test
    @TestID(103987)
    fun tryToCreateProfileWithFreeUser(){
        userDataHelper.setUserData(TestUser.freeUser)
        homeRobot.swipeLeftToOpenProfiles()
        profilesRobot.clickOnCreateNewProfileButton()
            .insertTextInProfileNameField(DefaultData.PROFILE_NAME)
            .verify { upgradeButtonIsDisplayed() }
    }
}
