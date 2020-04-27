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
import com.protonvpn.results.LoginFormResult;
import com.protonvpn.results.LoginResult;
import com.protonvpn.testsHelper.TestUser;
import com.protonvpn.testsHelper.UIActionsTestHelper;

public class LoginRobot extends UIActionsTestHelper {

    public LoginFormResult clickOnNeedHelpButton() {
        clickOnObjectWithIdAndText(R.id.textNeedHelp, R.string.loginNeedHelp);
        return new LoginFormResult();
    }

    public LoginResult login(TestUser user) {
        insertTextIntoFieldWithId(R.id.editEmail, user.email);
        insertTextIntoFieldWithId(R.id.editPassword, user.password);

        clickOnObjectWithIdAndText(R.id.buttonLogin, "Login");
        return new LoginResult(user);
    }

    public LoginFormResult viewUserPassword(TestUser user) {
        insertTextIntoFieldWithId(R.id.editEmail, user.email);
        insertTextIntoFieldWithId(R.id.editPassword, user.password);

        clickOnObjectWithId(R.id.text_input_password_toggle);

        return new LoginFormResult(user);
    }
}
