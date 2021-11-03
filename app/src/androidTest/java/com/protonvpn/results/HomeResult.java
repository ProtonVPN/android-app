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
import com.protonvpn.testsHelper.ServiceTestHelper;
import com.protonvpn.testsHelper.UIActionsTestHelper;

public class HomeResult extends UIActionsTestHelper {

    ServiceTestHelper serviceTestHelper = new ServiceTestHelper();

    public HomeResult dialogUpgradeVisible() {
        checkIfObjectWithIdAndTextIsDisplayed(R.id.textTitle, R.string.upgrade_secure_core_title);
        checkIfObjectWithIdAndTextIsDisplayed(R.id.textMessage, R.string.upgrade_secure_core_message);
        checkIfObjectWithIdAndTextIsDisplayed(R.id.buttonShowPlans, R.string.upgrade_see_plans_button);
        return this;
    }

    public HomeResult checkSecureCoreDisabled() {
        if (serviceTestHelper.isSecureCoreEnabled()) {
            throw new IllegalStateException("Secure core is enabled");
        }
        return this;
    }
}
