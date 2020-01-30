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

import com.protonvpn.android.R;
import com.protonvpn.android.ui.LoginActivity;
import com.protonvpn.testsHelper.NetworkTestHelper;
import com.protonvpn.testsHelper.TestUser;
import com.protonvpn.testsHelper.UIActionsTestHelper;

public class LoginResult extends UIActionsTestHelper {

    private TestUser user;

    public LoginResult(TestUser testUser) {
        user = testUser;
    }

    public LoginResult isSuccessful() {
        waitUntilObjectWithContentDescriptionAppearsInView(R.string.hamburgerMenu);
        return this;
    }

    public AccountResults usernameDisplayed() {
        clickOnObjectWithContentDescription(R.string.hamburgerMenu);

        //clicks on the user info button
        clickOnObjectWithId(R.id.layoutUserInfo);

        //checks if correct username is visible
        checkIfObjectWithIdAndTextIsDisplayed(R.id.textUser, user.email);

        return new AccountResults();
    }

    public LoginResult isFailure() {
        checkIfObjectWithIdIsNotDisplayed(R.id.fabQuickConnect);
        return this;
    }

    public LoginResult badCredentialsError() {
        waitUntilObjectWithTextAppearsInView("Incorrect login credentials. Please try again");
        return this;
    }

    public LoginResult noInternetConnectionError(LoginActivity activity) {
        NetworkTestHelper.waitUntilNetworkErrorAppears(activity.getNetworkFrameLayout());

        checkIfObjectWithIdIsDisplayed(R.id.buttonRetry);

        checkIfObjectWithIdAndTextIsDisplayed(R.id.textDescription, "No internet connection");
        return this;
    }

    public TestUser getUser() {
        return user;
    }

}
