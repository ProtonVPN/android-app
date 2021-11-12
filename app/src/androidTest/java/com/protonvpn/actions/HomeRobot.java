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
import com.protonvpn.results.LogoutResult;
import com.protonvpn.testsHelper.UIActionsTestHelper;

import androidx.annotation.NonNull;

import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static org.hamcrest.Matchers.endsWith;

public class HomeRobot extends UIActionsTestHelper {

    public void openDrawer() {
        if (!isDrawerOpened()) {
            clickOnObjectWithContentDescription(R.string.hamburgerMenu);
        }
    }

    public boolean isDrawerOpened() {
        return isObjectWithIdVisible(R.id.navigationDrawer);
    }

    public ConnectionResult connectThroughQuickConnect() {
        return connectThroughQuickConnect("Fastest");
    }

    public ConnectionResult connectThroughQuickConnect(@NonNull String profileName) {
        // The last "FloatingActionButton" is the main one.
        longClickOnLastChildWithId(R.id.fabQuickConnect, withClassName(endsWith("FloatingActionButton")));
        clickOnObjectWithText(profileName);
        if (!MockSwitch.mockedConnectionUsed) {
            allowToUseVpn();
        }
        return new ConnectionResult();
    }

    public void allowToUseVpn() {
        allowVpnToBeUsed(isAllowVpnRequestVisible());
    }

    public LogoutResult logout() {
        openDrawer();
        clickOnObjectWithIdAndText(R.id.drawerButtonLogout, R.string.menuActionSignOut);
        return new LogoutResult();
    }

    public LogoutResult logoutAfterWarning() {
        clickOnObjectWithText(R.string.logoutConfirmDialogButton);
        return new LogoutResult();
    }

    public LogoutResult cancelLogoutAfterWarning() {
        clickOnObjectWithText(R.string.cancel);
        return new LogoutResult();
    }
}
