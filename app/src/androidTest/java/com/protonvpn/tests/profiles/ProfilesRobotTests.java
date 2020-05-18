/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.tests.profiles;

import com.protonvpn.actions.ConnectionRobot;
import com.protonvpn.actions.HomeRobot;
import com.protonvpn.actions.ProfilesRobot;
import com.protonvpn.android.R;
import com.protonvpn.android.vpn.VpnState;
import com.protonvpn.results.ConnectionResult;
import com.protonvpn.results.ProfilesResult;
import com.protonvpn.tests.testRules.ProtonHomeActivityTestRule;
import com.protonvpn.tests.testRules.SetUserPreferencesRule;
import com.protonvpn.testsHelper.TestUser;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ServiceTestRule;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class ProfilesRobotTests {

    private HomeRobot homeRobot = new HomeRobot();

    @ClassRule public final static ServiceTestRule SERVICE_TEST_RULE = new ServiceTestRule();
    @ClassRule static public SetUserPreferencesRule testClassRule =
        new SetUserPreferencesRule(TestUser.getPlusUser());
    @Rule public ProtonHomeActivityTestRule testRule = new ProtonHomeActivityTestRule();

    @Test
    public void defaultProfileOptions() {
        ProfilesResult result = homeRobot.clickOnProfilesTab();
        result.isSuccess();
    }

    @Test
    public void tryToCreateProfileWithoutName() {
        String profileName = "";
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(profileName);
        ProfilesResult result = profilesRobot.clickOnSaveButton();
        result.isFailure().emptyProfileNameError();
    }

    @Test
    public void tryToCreateProfileWithoutCountry() {
        String profileName = "Test";
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(profileName);

        ProfilesResult result = profilesRobot.clickOnSaveButton();
        result.isFailure().emptyCountryError().profileIsVisible(profileName);
    }

    @Test
    public void tryToCreateProfileWithoutServer() {
        String profileName = "Test";
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectFirstCountry();

        ProfilesResult result = profilesRobot.clickOnSaveButton();
        result.isFailure().emptyServerError().profileIsVisible(profileName);
    }

    @Test
    public void tryToCreateProfile() {
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectFirstCountry();
        profilesRobot.selectRandomServer();

        ProfilesResult result = profilesRobot.clickOnSaveButton();
        result.isSuccess().profilesResult.profileIsVisible(profileName);
    }

    @Test
    public void tryToCreateProfileWithSelectedColors() {
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectColor(R.color.pickerColor3);
        profilesRobot.selectFirstCountry();
        profilesRobot.selectRandomServer();

        ProfilesResult result = profilesRobot.clickOnSaveButton();
        result.isSuccess().profilesResult.profileIsVisible(profileName);
    }

    @Test
    public void tryToConnectToProfile() {
        testRule.mockStatusOnConnect(VpnState.Connected.INSTANCE);
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectFirstCountry();
        profilesRobot.selectRandomServer();
        profilesRobot.clickOnSaveButton().isSuccess().profilesResult.profileIsVisible(profileName);

        ConnectionResult result = profilesRobot.selectProfile(profileName).clickOnConnectButton();
        result.isConnectedToVpn();

        new ConnectionRobot().clickDisconnectButton().isDisconnectedFromVpn();
    }

    @Test
    public void tryToGoBackWithoutSavingNewProfile() {
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectFirstCountry();
        profilesRobot.selectRandomServer();

        ProfilesResult result = profilesRobot.navigateBackFromForm();
        result.isFailure().notSavedProfileWarning();
    }

    @Test
    public void discardNewProfile() {
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectFirstCountry();
        profilesRobot.selectRandomServer();
        profilesRobot.navigateBackFromForm().notSavedProfileWarning();

        ProfilesResult result = profilesRobot.clickDiscardButton().isSuccess().profilesResult;
        result.profileIsNotVisible(profileName);
    }

    @Test
    public void cancelDiscardActionAndSaveProfile() {
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectFirstCountry();
        profilesRobot.selectRandomServer();
        profilesRobot.navigateBackFromForm().notSavedProfileWarning();
        profilesRobot.clickCancelButton();

        ProfilesResult result = profilesRobot.clickOnSaveButton();
        result.isSuccess().profilesResult.profileIsVisible(profileName);
    }

    @Test
    public void tryToSaveSecureCoreProfileWithoutExitCountry() {
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "TestFieldPrefill";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.enableSecureCore();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectSecondSecureCoreExitCountry();

        ProfilesResult result = profilesRobot.clickOnSaveButton().isSuccess().profilesResult;
        result.profileIsVisible(profileName);
    }

    @Test
    public void tryToSaveSecureCoreProfileWithoutEntryCountry() {
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.enableSecureCore();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectFirstSecureCoreExitCountry();

        ProfilesResult result = profilesRobot.clickOnSaveButton().isFailure();
        result.emptyEntryCountryError().profileIsVisible(profileName);
    }

    @Test
    public void saveSecureCoreProfile() {
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.enableSecureCore();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectSecondSecureCoreExitCountry();
        profilesRobot.selectSecureCoreEntryCountryForSecondExit();

        ProfilesResult result = profilesRobot.clickOnSaveButton().isSuccess().profilesResult;
        result.profileIsVisible(profileName);
    }

    @Test
    public void connectToCreatedSecureCoreProfile() {
        testRule.mockStatusOnConnect(VpnState.Connected.INSTANCE);
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.enableSecureCore();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectSecondSecureCoreExitCountry();
        profilesRobot.selectSecureCoreEntryCountryForSecondExit();
        profilesRobot.clickOnSaveButton().isSuccess().profilesResult.profileIsVisible(profileName);

        profilesRobot.selectProfile(profileName);

        ProfilesResult result = profilesRobot.clickOnConnectButton().isDisconnectedFromVpn().profilesResult;
        result.connectingToSecureCoreWarning().clickYesButton().isConnectedToVpn();

        new ConnectionRobot().clickDisconnectButton().isDisconnectedFromVpn();
    }

    @Test
    public void editSecureCoreProfile() {
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test profile";
        String newProfileName = "New test profile";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.enableSecureCore();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectSecondSecureCoreExitCountry();
        profilesRobot.selectSecureCoreEntryCountryForSecondExit();
        profilesRobot.clickOnSaveButton().isSuccess().profilesResult.profileIsVisible(profileName);

        profilesRobot.clickEditProfile();
        profilesRobot.updateProfileName(newProfileName);
        profilesRobot.clickOnSaveButton().isSuccess().profilesResult.profileIsVisible(newProfileName);
    }

    @Test
    public void deleteSecureCoreProfile() {
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test profile";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.enableSecureCore();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectSecondSecureCoreExitCountry();
        profilesRobot.selectSecureCoreEntryCountryForSecondExit();
        profilesRobot.clickOnSaveButton().isSuccess().profilesResult.profileIsVisible(profileName);

        profilesRobot.clickEditProfile();
        profilesRobot.clickDeleteProfile().profilesResult.profileIsNotVisible(profileName);
    }

    //Remove ignore after the bug will be fixed
    @Ignore
    @Test
    public void editProfile() {
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test profile";
        String newProfileName = "New test profile";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectFirstCountry();
        profilesRobot.selectRandomServer();
        profilesRobot.clickOnSaveButton().isSuccess().profilesResult.profileIsVisible(profileName);

        profilesRobot.clickEditProfile();
        profilesRobot.updateProfileName(newProfileName);
        profilesRobot.clickOnSaveButton().isSuccess().profilesResult.profileIsVisible(newProfileName);
    }

    @Test
    public void deleteProfile() {
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test profile";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectFirstCountry();
        profilesRobot.selectRandomServer();
        profilesRobot.clickOnSaveButton().isSuccess().profilesResult.profileIsVisible(profileName);

        profilesRobot.clickEditProfile();
        profilesRobot.clickDeleteProfile().profilesResult.profileIsNotVisible(profileName);
    }

    @Test
    public void createProfileWithOpenVPNTCPProtocol() {
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test profile";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectFirstCountry();
        profilesRobot.selectRandomServer();
        profilesRobot.selectOpenVPNProtocol();

        profilesRobot.clickOnSaveButton().isSuccess().profilesResult.profileIsVisible(profileName);
    }

    @Test
    public void createProfileWithOpenVPNUDPProtocol() {
        ProfilesRobot profilesRobot = homeRobot.clickOnProfilesTab().isSuccess();

        String profileName = "Test profile";

        profilesRobot.clickOnCreateNewProfileButton();
        profilesRobot.insertTextInProfileNameField(profileName);
        profilesRobot.selectFirstCountry();
        profilesRobot.selectRandomServer();
        profilesRobot.selectOpenVPNProtocol();
        profilesRobot.selectUDPTransmissionProtocol();

        profilesRobot.clickOnSaveButton().isSuccess().profilesResult.profileIsVisible(profileName);
    }
}
