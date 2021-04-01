/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import de.blinkt.openvpn.LaunchVPN;
import de.blinkt.openvpn.R;
import de.blinkt.openvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;

/**
 * Created by arne on 13.10.13.
 */
public class DisconnectVPN extends Activity implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
    private IOpenVPNServiceInternal mService;
    private ServiceConnection mConnection = new ServiceConnection() {



        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {

            mService = IOpenVPNServiceInternal.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
        }

    };

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = new Intent(this, OpenVPNService.class);
        intent.setAction(OpenVPNService.START_SERVICE);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        showDisconnectDialog();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(mConnection);
    }

    private void showDisconnectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_cancel);
        builder.setMessage(R.string.cancel_connection_query);
        builder.setNegativeButton(android.R.string.cancel, this);
        builder.setPositiveButton(R.string.cancel_connection, this);
        builder.setNeutralButton(R.string.reconnect, this);
        builder.setOnCancelListener(this);

        builder.show();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            ProfileManager.setConntectedVpnProfileDisconnected(this);
            if (mService != null) {
                try {
                    mService.stopVPN(false);
                } catch (RemoteException e) {
                    VpnStatus.logException(e);
                }
            }
        } else if (which == DialogInterface.BUTTON_NEUTRAL) {
            Intent intent = new Intent(this, LaunchVPN.class);
            intent.putExtra(LaunchVPN.EXTRA_KEY, VpnStatus.getLastConnectedVPNProfile());
            intent.setAction(Intent.ACTION_MAIN);
            startActivity(intent);
        }
        finish();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        finish();
    }
}
