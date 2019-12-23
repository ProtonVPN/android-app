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

import com.protonvpn.android.R;
import com.protonvpn.results.ConnectionResult;
import com.protonvpn.testsHelper.ConditionalActionsHelper;
import com.protonvpn.testsHelper.UIActionsTestHelper;

import static org.strongswan.android.logic.StrongSwanApplication.getContext;

public class ConnectionRobot extends UIActionsTestHelper {

    ConditionalActionsHelper conditionalActions = new ConditionalActionsHelper();

    public ConnectionResult clickDisconnectButton() {
        conditionalActions.clickOnDisconnectButtonUntilUserIsDisconnected();
        return new ConnectionResult();
    }

    public ConnectionResult clickCancelConnectionButton() {
        clickOnObjectWithIdAndText(R.id.buttonCancel, R.string.loaderCancel);
        return new ConnectionResult();
    }

    public ConnectionRobot checkIfNotReachableErrorAppears() {
        String errorMessage = getContext().getString(R.string.error_unreachable);
        checkIfObjectWithIdAndTextIsDisplayed(R.id.textError, errorMessage);
        return this;
    }

    public ConnectionResult clickCancelRetry() {
        clickOnObjectWithId(R.id.buttonCancelRetry);
        return new ConnectionResult();
    }

}

