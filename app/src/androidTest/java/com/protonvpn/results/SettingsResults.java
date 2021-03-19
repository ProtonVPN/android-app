/*
 * Copyright (c) 2019 Proton Technologies AG
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

import com.protonvpn.android.R;
import com.protonvpn.tests.upgrade.UpgradeTestData;
import com.protonvpn.testsHelper.ConditionalActionsHelper;
import com.protonvpn.testsHelper.UIActionsTestHelper;

public class SettingsResults extends UIActionsTestHelper {

    public SettingsResults settingsViewIsVisible() {
        checkIfObjectWithIdIsDisplayed(R.id.textStartOptions);
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithIdAppears(R.id.scrollView,
            R.id.buttonAlwaysOn);
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithIdAppears(R.id.scrollView,
            R.id.spinnerDefaultProtocol);
        return new SettingsResults();
    }

    public SettingsResults settingsMtuRangeInvalidError() {
        checkIfErrorMessageHasAppeared(R.string.settingsMtuRangeInvalid);
        return this;
    }

    public SettingsResults splitTunnelIPIsVisible() {
        ConditionalActionsHelper.scrollDownInViewWithIdUntilObjectWithIdAppears(R.id.scrollView, R.id.splitTunnelIPs);
        return this;
    }

    public SettingsResults checkIfRandomServerIsSelected() {
        checkIfObjectWithTextIsDisplayed("Random");
        return this;
    }

    public SettingsResults checkIfMTUIsSet() {
        checkIfObjectWithTextIsDisplayed(String.valueOf(UpgradeTestData.valueMTU));
        return this;
    }

    public SettingsResults checkIfIPAddressIsExcluded() {
        checkIfObjectWithTextIsDisplayed(UpgradeTestData.excludedIPAddress);
        return this;
    }
}
