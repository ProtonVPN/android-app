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
package com.protonvpn.tests.testRules;

import android.content.Intent;

import com.protonvpn.MockSwitch;
import com.protonvpn.android.ui.home.HomeActivity;

import static org.strongswan.android.logic.StrongSwanApplication.getContext;

public class TestIntent {

    private Intent intent;

    public TestIntent() {
        intent = new Intent(getContext(), HomeActivity.class);
        intent.putExtra("isTest", MockSwitch.mockedConnectionUsed);
    }

    public Intent getIntent() {
        return intent;
    }
}
