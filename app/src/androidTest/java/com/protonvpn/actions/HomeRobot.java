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
import com.protonvpn.results.ConnectionResult;
import com.protonvpn.results.HomeResult;
import com.protonvpn.results.LogoutResult;
import com.protonvpn.results.ProfilesResult;
import com.protonvpn.testsHelper.UIActionsTestHelper;

public class HomeRobot extends UIActionsTestHelper {

    public HomeRobot disableSecureCore() {
        setStateOfSecureCoreSwitch(false);
        return this;
    }

    public SettingsRobot openSettings() {
        clickOnObjectWithContentDescription(R.string.hamburgerMenu);
        clickOnObjectWithText("Settings");
        return new SettingsRobot();
    }

    public CountriesRobot clickOnCountriesTab() {
        clickOnObjectChildWithinChildWithIdAndPosition(R.id.tabs, 0, 0);
        return new CountriesRobot();
    }

    public MapRobot clickOnMapViewTab() {
        clickOnObjectChildWithinChildWithIdAndPosition(R.id.tabs, 0, 1);
        return new MapRobot();
    }

    public ProfilesResult clickOnProfilesTab() {
        clickOnObjectChildWithinChildWithIdAndPosition(R.id.tabs, 0, 2);
        return new ProfilesResult();
    }

    public ConnectionResult connectThroughQuickConnect() {
        longClickOnObjectChildWithIdAndPosition(R.id.fabQuickConnect, 2);
        clickOnObjectWithText("Fastest");
        if (!MockSwitch.mockedConnectionUsed) {
            allowToUseVpn();
        }
        return new ConnectionResult();
    }

    public ConnectionResult connectThroughQuickConnectWithoutVPNHandling() {
        longClickOnObjectChildWithIdAndPosition(R.id.fabQuickConnect, 2);
        handleQuickConnectLongClick();
        return new ConnectionResult();
    }

    public ConnectionResult disconnectThroughQuickConnect() {
        clickOnObjectChildWithIdAndPosition(R.id.fabQuickConnect, 3);
        return new ConnectionResult();
    }

    public ConnectionResult allowToUseVpn() {
        allowVpnToBeUsed(isAllowVpnRequestVisible());
        return new ConnectionResult();
    }

    public HomeRobot handleQuickConnectLongClick() {
        clickOnRandomButtonFromQuickConnectMenu(isLongClickOnQuickConnect());
        return this;
    }

    public LogoutResult logout() {
        //clicks on the menu
        clickOnObjectWithContentDescription(R.string.hamburgerMenu);

        //clicks on the logout button
        clickOnObjectWithIdAndText(R.id.drawerButtonLogout, R.string.menuActionLogout);

        return new LogoutResult();
    }

    public LogoutResult logoutAfterWarning() {
        clickOnObjectWithIdAndText(R.id.md_buttonDefaultPositive, "OK");
        return new LogoutResult();
    }

    public LogoutResult cancelLogoutAfterWarning() {
        clickOnObjectWithText(R.string.cancel);
        return new LogoutResult();
    }

    public HomeRobot enableSecureCore() {
        setStateOfSecureCoreSwitch(true);
        return this;
    }

    public HomeResult clickButtonGotIt() {
        clickOnObjectWithId(R.id.buttonGotIt);
        return new HomeResult();
    }

    public HomeResult clickButtonCancel() {
        clickOnObjectWithId(R.id.md_buttonDefaultNegative);
        return new HomeResult();
    }

    public HomeResult clickButtonUpgrade() {
        clickOnObjectWithId(R.id.md_buttonDefaultPositive);
        return new HomeResult();
    }
}
