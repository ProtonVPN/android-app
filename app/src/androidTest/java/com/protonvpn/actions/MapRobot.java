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
import com.protonvpn.testsHelper.ConditionalActionsHelper;
import com.protonvpn.testsHelper.UIActionsTestHelper;

public class MapRobot extends UIActionsTestHelper {

    ConditionalActionsHelper conditionalActionsHelper;

    public MapRobot() {
        conditionalActionsHelper = new ConditionalActionsHelper();
    }

    public MapRobot clickOnUSNode() {
        conditionalActionsHelper.clickOnMapNodeUntilConnectButtonAppears("United States");
        return this;
    }

    public MapRobot clickOnSecureCoreSwedenNode() {
        clickOnMapNode("Sweden");
        return this;
    }

    public MapRobot clickOnSecureCoreFranceNode() {
        conditionalActionsHelper.clickOnMapNodeUntilConnectButtonAppears("Sweden >> France");
        return this;
    }

    public MapRobot clickOnSelectedSecureCoreFranceNode() {
        conditionalActionsHelper.clickOnMapNodeUntilConnectButtonAppears("Sweden >> France Selected");
        return this;
    }

    public ConnectionResult clickConnectButton() {
        clickOnObjectWithIdAndText(R.id.buttonConnect, R.string.connect);
        if (!MockSwitch.mockedConnectionUsed) {
            new HomeRobot().allowToUseVpn();
        }
        return new ConnectionResult();
    }

    public ConnectionResult clickConnectButtonWithoutVpnHandling() {
        clickOnObjectWithIdAndText(R.id.buttonConnect, R.string.connect);
        return new ConnectionResult();
    }

    public MapRobot enableSecureCore() {
        clickOnObjectWithId(R.id.switchSecureCore);
        return this;
    }

    public ConnectionResult clickDisconnectButton() {
        clickOnObjectWithText(R.string.disconnect);
        return new ConnectionResult();
    }
}
