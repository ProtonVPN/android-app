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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.protonvpn.android.R;
import com.protonvpn.android.components.BaseActivity;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesDialogActivity;
import com.protonvpn.android.ui.planupgrade.UpgradeSecureCoreDialogActivity;
import com.protonvpn.android.utils.Log;
import com.protonvpn.android.vpn.VpnConnectionManager;

import org.strongswan.android.logic.CharonVpnService;

import java.io.File;

import javax.inject.Inject;

import androidx.annotation.NonNull;

public abstract class VpnActivity extends BaseActivity {

    private VpnStateService mService;
    @Inject UserData userData;
    @Inject protected VpnConnectionManager vpnConnectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerForEvents();
        Log.checkForLogTruncation(getFilesDir() + File.separator + CharonVpnService.LOG_FILE);
    }

    public final void onConnect(@NonNull Profile profileToConnect) {
        onConnect(profileToConnect, "mobile home screen (unspecified)");
    }

    public void onConnect(@NonNull Profile profileToConnect, @NonNull String connectionCauseLog) {
        Server server = profileToConnect.getServer();
        if ((userData.hasAccessToServer(server) && server.getOnline()) || server == null) {
            vpnConnectionManager.connect(this, profileToConnect, connectionCauseLog);
        }
        else {
            connectingToRestrictedServer(profileToConnect.getServer());
        }
    }

    protected void showSecureCoreUpgradeDialog() {
        startActivity(new Intent(this, UpgradeSecureCoreDialogActivity.class));
    }

    protected void showPlusUpgradeDialog() {
        startActivity(new Intent(this, UpgradePlusCountriesDialogActivity.class));
    }

    private void connectingToRestrictedServer(Server server) {
        if (server.getOnline()) {
            showPlusUpgradeDialog();
        } else {
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restrictedMaintenanceTitle)
                .setMessage(R.string.restrictedMaintenanceDescription)
                .setNegativeButton(R.string.got_it, null)
                .show();
        }
    }
}
