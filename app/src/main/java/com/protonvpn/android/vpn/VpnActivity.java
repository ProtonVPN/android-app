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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.protonvpn.android.R;
import com.protonvpn.android.components.BaseActivity;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.utils.HtmlTools;
import com.protonvpn.android.utils.Log;
import com.protonvpn.android.utils.ServerManager;

import org.strongswan.android.logic.CharonVpnService;
import org.strongswan.android.logic.TrustedCertificateManager;
import org.strongswan.android.logic.VpnStateService;

import java.io.File;

import javax.inject.Inject;

import de.blinkt.openpvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openpvpn.core.OpenVPNService;

public abstract class VpnActivity extends BaseActivity {

    private static final int PREPARE_VPN_SERVICE = 0;
    private static final String DIALOG_TAG = "Dialog";
    private static final String URL_SUPPORT_PERMISSIONS = "https://protonvpn.com/support/android-vpn-permissions-problem";
    private Profile server;
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

    /**
     * Prepare the VpnService. If this succeeds the current VPN profile is
     * started.
     *
     * @param profileInfo a bundle containing the information about the profile to be started
     */
    protected void prepareVpnService(Profile profileInfo) {
        Intent intent;
        try {
            intent = VpnService.prepare(this);
        }
        catch (IllegalStateException ex) {
            /* this happens if the always-on VPN feature (Android 4.2+) is activated */
            VpnNotSupportedError.showWithMessage(this, R.string.strongswan_vpn_not_supported);
            return;
        }
        /* store profile info until the user grants us permission */
        server = profileInfo;
        if (intent != null) {
            try {
                startActivityForResult(intent, PREPARE_VPN_SERVICE);
            }
            catch (ActivityNotFoundException ex) {
                /* it seems some devices, even though they come with Android 4,
                 * don't have the VPN components built into the system image.
                 * com.android.vpndialogs/com.android.vpndialogs.ConfirmDialog
                 * will not be found then */
                VpnNotSupportedError.showWithMessage(this, R.string.strongswan_vpn_not_supported);
            }
        }
        else {    /* user already granted permission to use VpnService */
            onActivityResult(PREPARE_VPN_SERVICE, RESULT_OK, null);
        }
    }

    public void onConnect(Profile profileToConnect) {
        Server server = profileToConnect.getServer();
        if ((userData.hasAccessToServer(server) && server.isOnline()) || server == null) {
            onVpnProfileSelected(profileToConnect);
        }
        else {
            connectingToRestrictedServer(profileToConnect.getServer());
        }
    }

    private void connectingToRestrictedServer(Server server) {
        if (server.isOnline()) {
            new MaterialDialog.Builder(this).theme(Theme.DARK)
                .title(server.isSecureCoreServer() ? R.string.restrictedSecureCoreTitle :
                    server.isPlusServer() ? R.string.restrictedPlusTitle : R.string.restrictedBasicTitle)
                .content(server.isSecureCoreServer() ? R.string.restrictedSecureCore :
                    server.isPlusServer() ? R.string.restrictedPlus : R.string.restrictedBasic)
                .positiveText("Upgrade")
                .onPositive((dialog, which) -> openUrl("https://account.protonvpn.com/dashboard"))
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PREPARE_VPN_SERVICE:
                if (resultCode == RESULT_OK) {
                    vpnStateMonitor.connect(server);
                }
                else if (resultCode == RESULT_CANCELED && Build.VERSION.SDK_INT >= 24) {
                    showNoVpnPermissionDialog();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @TargetApi(24)
    private void showNoVpnPermissionDialog() {
        new MaterialDialog.Builder(this).theme(Theme.DARK)
                .title(R.string.error_prepare_vpn_title)
                .content(HtmlTools.fromHtml(getString(R.string.error_prepare_vpn_description, URL_SUPPORT_PERMISSIONS)))
                .positiveText(R.string.error_prepare_vpn_settings)
                .onPositive((dialog, which) -> startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)))
                .show();
    }

    public void onVpnProfileSelected(Profile profile) {
        if (getIntent().getBooleanExtra("isTest", false)) {
            vpnStateMonitor.connect(profile);
        }
        else {
            prepareVpnService(profile);
        }
    }

    private void startVpnProfile(Profile server) {
        if (server != null) {
            onVpnProfileSelected(server);
        }
        else {
            Toast.makeText(this, R.string.strongswan_profile_not_found, Toast.LENGTH_LONG).show();
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

    /**
     * Dismiss dialog if shown
     */
    public void removeFragmentByTag(String tag) {
        FragmentManager fm = getFragmentManager();
        Fragment login = fm.findFragmentByTag(tag);
        if (login != null) {
            FragmentTransaction ft = fm.beginTransaction();
            ft.remove(login);
            ft.commit();
        }
    }

    /**
     * Class representing an error message which is displayed if VpnService is
     * not supported on the current device.
     */
    public static class VpnNotSupportedError extends DialogFragment {

        static final String ERROR_MESSAGE_ID = "org.strongswan.android.VpnNotSupportedError.MessageId";

        public static void showWithMessage(Activity activity, int messageId) {
            Bundle bundle = new Bundle();
            bundle.putInt(ERROR_MESSAGE_ID, messageId);
            VpnNotSupportedError dialog = new VpnNotSupportedError();
            dialog.setArguments(bundle);
            dialog.show(activity.getFragmentManager(), DIALOG_TAG);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Bundle arguments = getArguments();
            final int messageId = arguments.getInt(ERROR_MESSAGE_ID);
            return new AlertDialog.Builder(getActivity()).setTitle(
                R.string.strongswan_vpn_not_supported_title)
                .setMessage(messageId)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, id) -> dialog.dismiss())
                .create();
        }
    }
}
