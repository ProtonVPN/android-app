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
package com.protonvpn.android.vpn;

import com.protonvpn.android.components.NotificationHelper;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.utils.Constants;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.utils.Storage;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import dagger.android.AndroidInjection;
import de.blinkt.openpvpn.VpnProfile;
import de.blinkt.openpvpn.core.OpenVPNService;
import de.blinkt.openpvpn.core.VpnStatus;

public class OpenVPNWrapperService extends OpenVPNService implements VpnStatus.StateListener {

    @Inject UserData userData;

    @Inject VpnStateMonitor stateMonitor;
    @Inject ServerManager serverManager;

    @Override
    public void onCreate() {
        super.onCreate();
        AndroidInjection.inject(this);
        NotificationHelper.INSTANCE.initNotificationChannel(getApplicationContext());
        startForeground(Constants.NOTIFICATION_ID, stateMonitor.buildNotification());
    }

    @Nullable
    @Override
    public VpnProfile getProfile() {
        Server serverToConnect = Storage.load(Server.class);
        if (serverToConnect != null) {
            if (serverToConnect.notReadyForConnection()) {
                serverToConnect.prepareForConnection(userData);
            }
            return serverToConnect.openVPNProfile(this, userData,
                stateMonitor.getConnectionProfile().getTransmissionProtocol(userData));
        }
        else {
            return null;
        }
    }

    @Override
    protected boolean onProcessRestore() {
        Server lastServer = Storage.load(Server.class);
        if (lastServer == null) {
            return false;
        }

        Profile profile = Profile.getTemptProfile(lastServer, serverManager);
        return stateMonitor.onRestoreProcess(profile) && profile.isOpenVPNSelected(userData);
    }
}
