/*
 * Copyright (c) 2021 Proton Technologies AG
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
package com.protonvpn.android.ui.home.vpn;

import android.os.Bundle;

import com.protonvpn.android.components.BaseActivity;
import com.protonvpn.android.logging.LogEventsKt;
import com.protonvpn.android.logging.ProtonLogger;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.redesign.vpn.AnyConnectIntent;
import com.protonvpn.android.vpn.ConnectTrigger;
import com.protonvpn.android.vpn.VpnConnectionManager;

import javax.inject.Inject;

import androidx.annotation.NonNull;

public abstract class VpnActivity extends BaseActivity {

    @Inject protected VpnConnectionManager vpnConnectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerForEvents();
    }

    public void onConnect(@NonNull AnyConnectIntent connectIntent, @NonNull ConnectTrigger trigger) {
        ProtonLogger.INSTANCE.log(LogEventsKt.UiConnect, trigger.getDescription());
        vpnConnectionManager.connect(getVpnUiDelegate(), connectIntent, trigger);
    }
}
