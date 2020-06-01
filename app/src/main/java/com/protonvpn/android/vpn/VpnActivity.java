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
package com.protonvpn.android.vpn;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.protonvpn.android.R;
import com.protonvpn.android.components.BaseActivity;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.utils.Constants;
import com.protonvpn.android.utils.Log;
import com.protonvpn.android.utils.ServerManager;

import org.strongswan.android.logic.CharonVpnService;
import org.strongswan.android.logic.TrustedCertificateManager;
import org.strongswan.android.logic.VpnStateService;

import java.io.File;

import javax.inject.Inject;

import de.blinkt.openpvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openpvpn.core.OpenVPNService;

import static com.protonvpn.android.utils.AndroidUtilsKt.openProtonUrl;

public abstract class VpnActivity extends BaseActivity {

    private VpnStateService mService;
    @Inject ServerManager serverManager;
    @Inject UserData userData;
    @Inject VpnStateMonitor vpnStateMonitor;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((VpnStateService.LocalBinder) service).getService();
        }
    };
    private IOpenVPNServiceInternal oVPNService;
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {

            oVPNService = IOpenVPNServiceInternal.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            oVPNService = null;
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.bindService(new Intent(this, VpnStateService.class), mServiceConnection,
            Service.BIND_AUTO_CREATE);
        registerForEvents();
        Log.checkForLogTruncation(getFilesDir() + File.separator + CharonVpnService.LOG_FILE);
        new LoadCertificatesTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = new Intent(this, OpenVPNService.class);
        intent.setAction(OpenVPNService.START_SERVICE);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(mConnection);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mService != null) {
            this.unbindService(mServiceConnection);
        }
    }

    public void onConnect(Profile profileToConnect) {
        Server server = profileToConnect.getServer();
        if ((userData.hasAccessToServer(server) && server.getOnline()) || server == null) {
            vpnStateMonitor.connect(this, profileToConnect);
        }
        else {
            connectingToRestrictedServer(profileToConnect.getServer());
        }
    }

    private void connectingToRestrictedServer(Server server) {
        if (server.getOnline()) {
            new MaterialDialog.Builder(this).theme(Theme.DARK)
                .title(server.isSecureCoreServer() ? R.string.restrictedSecureCoreTitle :
                    server.isPlusServer() ? R.string.restrictedPlusTitle : R.string.restrictedBasicTitle)
                .content(server.isSecureCoreServer() ? R.string.restrictedSecureCore :
                    server.isPlusServer() ? R.string.restrictedPlus : R.string.restrictedBasic)
                .positiveText(R.string.upgrade)
                .onPositive((dialog, which) -> openProtonUrl(this, Constants.DASHBOARD_URL))
                .negativeText(R.string.cancel)
                .show();
        }
        else {
            new MaterialDialog.Builder(this).theme(Theme.DARK)
                .title(R.string.restrictedMaintenanceTitle)
                .content(R.string.restrictedMaintenanceDescription)
                .negativeText(R.string.cancel)
                .show();
        }
    }

    /**
     * Class that loads the cached CA certificates.
     */
    private class LoadCertificatesTask extends AsyncTask<Void, Void, TrustedCertificateManager> {

        @Override
        protected void onPreExecute() {
            setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected TrustedCertificateManager doInBackground(Void... params) {
            return TrustedCertificateManager.getInstance().load();
        }

        @Override
        protected void onPostExecute(TrustedCertificateManager result) {
            setProgressBarIndeterminateVisibility(false);
        }
    }
}
