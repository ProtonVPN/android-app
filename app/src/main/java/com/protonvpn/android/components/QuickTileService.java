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
package com.protonvpn.android.components;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.protonvpn.android.R;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.ui.login.LoginActivity;
import com.protonvpn.android.utils.ProtonLogger;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.vpn.VpnStateMonitor;

import javax.inject.Inject;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Observer;
import dagger.android.AndroidInjection;

import static android.os.Build.VERSION_CODES.N;

@RequiresApi(N)
public class QuickTileService extends TileService {

    @Inject ServerManager manager;
    @Inject UserData userData;
    @Inject VpnStateMonitor stateMonitor;

    private Observer<VpnStateMonitor.VpnState> stateInfoObserver = this::stateChanged;

    @Override
    public void onCreate() {
        super.onCreate();
        AndroidInjection.inject(this);
    }

    @Override
    public void onDestroy() {
        stateMonitor.getVpnState().removeObserver(stateInfoObserver);
        super.onDestroy();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_proton));
            bindToListener();
        }
    }

    private void bindToListener() {
        stateMonitor.getVpnState().observeForever(stateInfoObserver);
    }

    @Override
    public void onClick() {
        if (getQsTile().getState() == Tile.STATE_INACTIVE) {
            if (userData.isLoggedIn()) {
                Profile profile = manager.getDefaultConnection();
                if (profile != null) {
                    ProtonLogger.INSTANCE.log("Connecting via quick tile");
                    stateMonitor.connect(this, profile);
                }
            }
            else {
                Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }
        else {
            ProtonLogger.INSTANCE.log("Disconnecting via quick tile");
            stateMonitor.disconnect();
        }
    }

    public void stateChanged(VpnStateMonitor.VpnState vpnState) {
        switch (vpnState.getState()) {
            case DISABLED:
                getQsTile().setLabel(
                        getString(userData.isLoggedIn() ? R.string.quickConnect : R.string.login));
                getQsTile().setState(Tile.STATE_INACTIVE);
                break;
            case CHECKING_AVAILABILITY:
            case CONNECTING:
                getQsTile().setLabel(getString(R.string.state_connecting));
                getQsTile().setState(Tile.STATE_UNAVAILABLE);
                break;
            case CONNECTED:
                Server server = vpnState.getServer();
                String serverName = server.getName();
                getQsTile().setLabel(getString(R.string.tileConnected, serverName));
                getQsTile().setState(Tile.STATE_ACTIVE);
                break;
            case DISCONNECTING:
                getQsTile().setLabel(getString(R.string.state_disconnecting));
                getQsTile().setState(Tile.STATE_UNAVAILABLE);
                break;
        }

        getQsTile().updateTile();
    }
}
