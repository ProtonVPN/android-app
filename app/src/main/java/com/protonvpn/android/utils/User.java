/*
 * Copyright (c) 2017 Proton Technologies AG
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.protonvpn.android.models.login.LoginResponse;
import com.protonvpn.android.models.vpn.DefaultPorts;
import com.protonvpn.android.models.vpn.OpenVPNConfig;
import com.protonvpn.android.models.vpn.OpenVPNConfigResponse;

// Should be refactored and removed
@Deprecated
public class User {

    private static OpenVPNConfig openVPNConfig;

    public static String getUuid() {
        LoginResponse user = Storage.load(LoginResponse.class);
        return user != null ? user.getUid() : "";
    }

    public static String getAccessToken() {
        LoginResponse user = Storage.load(LoginResponse.class);
        return user != null ? "Bearer " + user.getAccessToken() : "";
    }

    @NonNull
    public static DefaultPorts getOpenVPNPorts() {
        return openVPNConfig == null ? DefaultPorts.getDefaults() : openVPNConfig.getDefaultPorts();
    }

    public static void setOpenVPNConfig(@Nullable OpenVPNConfigResponse config) {
        openVPNConfig =
            config == null ? new OpenVPNConfig(DefaultPorts.getDefaults()) : config.getOpenVPNConfig();
    }
}