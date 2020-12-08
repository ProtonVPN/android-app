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
package com.protonvpn.android.utils;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class DeepLinkActivity extends Activity {

    public static String FROM_DEEPLINK = "DeepLink";
    public static String USER_NAME = "UserName";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent in = getIntent();
        Uri data = in.getData();
        if (data != null && data.toString().contains("protonvpn://registered")) {
            String id = data.getQueryParameter("username");
            Intent login = new Intent(this, Constants.INSTANCE.getLOGIN_ACTIVITY_CLASS());
            login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            login.putExtra(FROM_DEEPLINK, true);
            login.putExtra(USER_NAME, id);
            startActivity(login);
        }
        finish();
    }
}