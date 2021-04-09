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
package com.protonvpn.results;

import com.protonvpn.actions.ProfilesRobot;
import com.protonvpn.android.R;
import com.protonvpn.testsHelper.UIActionsTestHelper;

import static org.strongswan.android.logic.StrongSwanApplication.getContext;

public class ProfilesResult extends UIActionsTestHelper {

    public ProfilesRobot isSuccess() {
        checkIfObjectWithTextIsDisplayed("Random");
        checkIfObjectWithTextIsDisplayed("Fastest");
        return new ProfilesRobot();
    }

    public ProfilesRobot profileIsVisible(String profileName) {
        checkIfObjectWithTextIsDisplayed(profileName);
        return new ProfilesRobot();
    }

    public ProfilesRobot profileIsNotVisible(String profileName) {
        checkIfObjectWithTextIsNotDisplayed(profileName);
        return new ProfilesRobot();
    }

    public ProfilesResult isFailure() {
        checkIfObjectWithTextIsNotDisplayed("Random");
        checkIfObjectWithTextIsNotDisplayed("Fastest");
        return this;
    }

    public ProfilesResult nonAccessibleServersVisible() {
        checkIfObjectWithTextIsDisplayed("Random (Upgrade)");
        checkIfObjectWithTextIsDisplayed("Fastest (Upgrade)");
        return this;
    }

    public ProfilesResult emptyProfileNameError() {
        checkIfObjectWithTextIsDisplayed(R.string.errorEmptyName);
        return this;
    }

    public ProfilesResult emptyCountryError() {
        checkIfObjectWithTextIsDisplayed(R.string.errorEmptyCountry);
        return this;
    }

    public ProfilesResult emptyServerError() {
        checkIfObjectWithTextIsDisplayed(R.string.errorEmptyServer);
        return this;
    }

    public ProfilesResult emptyExitCountryError() {
        checkIfObjectWithTextIsDisplayed(R.string.errorEmptyExitCountry);
        return this;
    }

    public ProfilesResult emptyEntryCountryError() {
        checkIfObjectWithTextIsDisplayed(R.string.errorEmptyEntryCountry);
        return this;
    }

    public ProfilesRobot notSavedProfileWarning() {
        checkIfObjectWithTextIsDisplayedInDialog("There are unsaved changes. Are you sure you want to discard them?");
        return new ProfilesRobot();
    }

    public ProfilesRobot connectingToSecureCoreWarning() {
        checkIfObjectWithTextIsNotDisplayed(getContext().getString(R.string.secureCoreSwitchOn));
        return new ProfilesRobot();
    }
}
