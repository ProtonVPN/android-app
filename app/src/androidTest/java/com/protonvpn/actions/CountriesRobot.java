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
import com.protonvpn.testsHelper.UIActionsTestHelper;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

public class CountriesRobot extends UIActionsTestHelper {

    public CountriesRobot selectCountry(String country) {
        clickOnObjectWithText(country);
        return this;
    }

    public CountriesRobot tryToSelectCountry(String country) {
        checkIfObjectWithTextIsNotDisplayed(country);
        return this;
    }

    public CountriesRobot selectFastestServer() {
        onView(withId(R.id.list)).perform(actionOnItemAtPosition(2, click()));
        return this;
    }

    public ConnectionResult clickConnectButton() {
        clickOnObjectWithIdAndText(R.id.buttonConnect, R.string.connect);
        if (!MockSwitch.mockedConnectionUsed) {
            new HomeRobot().allowToUseVpn();
        }
        return new ConnectionResult();
    }

}
