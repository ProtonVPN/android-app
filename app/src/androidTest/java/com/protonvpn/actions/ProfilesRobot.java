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
package com.protonvpn.actions;

import com.protonvpn.MockSwitch;
import com.protonvpn.android.R;
import com.protonvpn.android.models.config.TransmissionProtocol;
import com.protonvpn.android.models.config.VpnProtocol;
import com.protonvpn.android.models.vpn.VpnCountry;
import com.protonvpn.results.ConnectionResult;
import com.protonvpn.results.ProfilesResult;
import com.protonvpn.testsHelper.ConditionalActionsHelper;
import com.protonvpn.testsHelper.UIActionsTestHelper;

public class ProfilesRobot extends UIActionsTestHelper {

    public ServiceRobot serviceRobot;
    public ProfilesResult profilesResult;

    public ProfilesRobot() {
        serviceRobot = new ServiceRobot();
        profilesResult = new ProfilesResult();
    }

    public ProfilesRobot selectColor(final int resourceId) {
        clickOnObjectWithContentDescription(resourceId);
        return this;
    }

    public ProfilesRobot clickOnFastestOption() {
        clickOnObjectWithText("Fastest");
        return this;
    }

    public ConnectionResult clickOnConnectButton(String profileName) {
        clickOnObjectWithIdAndContentDescription(R.id.buttonConnect, profileName);
        if (!MockSwitch.mockedConnectionUsed) {
            new HomeRobot().allowToUseVpn();
        }
        return new ConnectionResult();
    }

    public ProfilesRobot clickOnCreateNewProfileButton() {
        clickOnObjectWithText("CREATE NEW PROFILE");
        return this;
    }

    public ProfilesRobot insertTextInProfileNameField(String text) {
        insertTextIntoFieldWithId(R.id.editName, text);
        return this;
    }

    public ProfilesRobot selectFirstCountry() {
        clickOnObjectWithId(R.id.spinnerCountry);
        clickOnObjectWithText(serviceRobot.getFirstCountryFromBackend());
        return this;
    }

    public ProfilesRobot selectFirstSecureCoreExitCountry() {
        clickOnObjectWithId(R.id.spinnerCountry);
        clickOnObjectWithText(serviceRobot.getFirstSecureCoreExitCountryFromBackend().getCountryName());
        return this;
    }

    public ProfilesRobot selectSecondSecureCoreExitCountry() {
        clickOnObjectWithId(R.id.spinnerCountry);
        clickOnObjectWithText(serviceRobot.getSecondSecureCoreExitCountryFromBackend().getCountryName());
        return this;
    }

    public ProfilesRobot selectSecureCoreEntryCountryForSecondExit() {
        VpnCountry exitCountry = serviceRobot.getSecondSecureCoreExitCountryFromBackend();
        clickOnObjectWithId(R.id.spinnerServer);
        clickOnObjectWithText(serviceRobot.getSecureCoreEntryCountryFromBackend(exitCountry));
        return this;
    }

    public ProfilesRobot selectFirstNotAccessibleVpnCountry() {
        clickOnObjectWithId(R.id.spinnerCountry);
        clickOnObjectWithText(serviceRobot.getFirstNotAccessibleVpnCountryFromBackend() + " (Upgrade)");
        return this;
    }

    public ProfilesRobot selectRandomServer() {
        clickOnObjectWithId(R.id.spinnerServer);
        // TODO Use "Random" instead of Fastest once random profile problems are solved
        clickOnObjectWithText("Fastest");
        return this;
    }

    public ProfilesResult selectNonAccessibleRandomServer() {
        clickOnObjectWithId(R.id.spinnerServer);
        clickOnObjectWithText("Random (Upgrade)");
        return new ProfilesResult();
    }

    public ProfilesResult clickOnSaveButton() {
        clickOnObjectWithId(R.id.fabSave);
        return new ProfilesResult();
    }

    public ProfilesRobot selectProfile(String profileName) {
        clickOnObjectWithText(profileName);
        return this;
    }

    public ProfilesResult navigateBackFromForm() {
        pressDeviceBackButton();
        return new ProfilesResult();
    }

    public ProfilesResult clickCancelButton() {
        clickOnObjectWithText(R.string.cancel);
        return new ProfilesResult();
    }

    public ProfilesResult clickDiscardButton() {
        clickOnObjectWithText("Discard");
        return new ProfilesResult();
    }

    public ProfilesRobot enableSecureCore() {
        clickOnObjectWithText("Secure Core");
        return this;
    }

    public ConnectionResult clickYesButton() {
        clickOnObjectWithId(R.id.md_buttonDefaultPositive);
        return new ConnectionResult();
    }

    public ConnectionResult clickOnUpgradeButton(String contentDescription) {
        clickOnObjectWithIdAndContentDescription(R.id.buttonUpgrade, contentDescription);
        return new ConnectionResult();
    }

    public ProfilesRobot clickEditProfile() {
        clickOnObjectWithId(R.id.profile_edit_button);
        return this;
    }

    public ProfilesRobot updateProfileName(String newProfileName) {
        insertTextIntoFieldWithId(R.id.editName, newProfileName);
        return this;
    }

    public ProfilesRobot clickDeleteProfile() {
        clickOnObjectWithId(R.id.action_delete);
        clickOnObjectWithText("Delete");
        return this;
    }

    public ProfilesRobot selectOpenVPNProtocol() {
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithIdAppears(R.id.coordinator,
            R.id.spinnerDefaultProtocol);
        clickOnObjectWithId(R.id.spinnerDefaultProtocol);
        clickOnObjectWithText(VpnProtocol.OpenVPN.toString());
        return this;
    }

    public ProfilesRobot selectUDPTransmissionProtocol() {
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithIdAppears(R.id.coordinator,
            R.id.spinnerTransmissionProtocol);
        clickOnObjectWithId(R.id.spinnerTransmissionProtocol);
        clickOnObjectWithText(TransmissionProtocol.UDP.toString());
        return this;
    }
}
