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
import com.protonvpn.testsHelper.UIActionsTestHelper;

public class HomeResult extends UIActionsTestHelper {

    public HomeResult dialogWelcomeIsVisible() {
        checkIfObjectWithTextIsDisplayed("Welcome on board");
        checkIfObjectWithIdIsDisplayed(R.id.image);
        checkIfObjectWithIdIsDisplayed(R.id.textTitle);
        checkIfObjectWithIdIsDisplayed(R.id.textDescription);
        checkIfObjectWithIdIsDisplayed(R.id.buttonGotIt);
        return this;
    }

    public HomeResult isSuccessful() {
        checkIfObjectWithIdIsDisplayed(R.id.fabQuickConnect);
        return this;
    }

    public HomeResult dialogAttentionTrialExpiredIsVisible() {
        checkIfObjectWithIdIsDisplayed(R.id.md_title);
        checkIfObjectWithIdIsDisplayed(R.id.md_content);
        checkIfObjectWithIdIsDisplayed(R.id.md_buttonDefaultNegative);
        checkIfObjectWithIdIsDisplayed(R.id.md_buttonDefaultPositive);
        return this;
    }

    public HomeResult upgradeButtonHasLink() {
        checkIfButtonOpensUrl(R.id.md_buttonDefaultPositive);
        return this;
    }
}
