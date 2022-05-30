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

import android.content.Intent;
import android.os.Bundle;

import com.protonvpn.android.components.BaseActivity;
import com.protonvpn.android.logging.LogEventsKt;
import com.protonvpn.android.logging.ProtonLogger;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.ui.planupgrade.UpgradeSecureCoreDialogActivity;
import com.protonvpn.android.utils.Log;
import com.protonvpn.android.vpn.VpnConnectionManager;

import org.strongswan.android.logic.CharonVpnService;

import java.io.File;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class VpnActivity extends BaseActivity {

    @Inject protected VpnConnectionManager vpnConnectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerForEvents();
        Log.checkForLogTruncation(getFilesDir() + File.separator + CharonVpnService.LOG_FILE);
    }

    public final void onConnect(@Nullable String uiElement, @NonNull Profile profileToConnect) {
        onConnect(profileToConnect, uiElement != null ? uiElement : "mobile home screen (unspecified)");
    }

    public void onConnect(@NonNull Profile profileToConnect, @NonNull String uiElement) {
        ProtonLogger.INSTANCE.log(LogEventsKt.UiConnect, uiElement);
        vpnConnectionManager.connect(getVpnUiDelegate(), profileToConnect, "user via " + uiElement);
    }
}
