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
package com.protonvpn.testsHelper;

import android.content.res.AssetManager;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.protonvpn.android.models.vpn.Server;
import com.google.gson.Gson;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.List;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

public class MockedServers {

    private static String json = null;
    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static Type listType = new TypeToken<List<Server>>() {}.getType();

    public static List<Server> getServerList() {
        AssetManager manager = getApplicationContext().getAssets();

        try {
            InputStream file = manager.open("MockedServers/Servers.json");
            int size = file.available();
            byte[] buffer = new byte[size];
            file.read(buffer);
            json = new String(buffer, "UTF-8");
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return gson.fromJson(json, listType);
    }
}


