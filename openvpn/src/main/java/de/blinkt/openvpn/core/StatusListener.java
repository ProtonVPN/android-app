/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import de.blinkt.openvpn.BuildConfig;
import de.blinkt.openvpn.core.VpnStatus.LogLevel;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

/**
 * Created by arne on 09.11.16.
 */

public class StatusListener implements VpnStatus.LogListener {
    private File mCacheDir;
    private Context mContext;
    private final IStatusCallbacks mCallback = new IStatusCallbacks.Stub() {
        @Override
        public void newLogItem(LogItem item) throws RemoteException {
            VpnStatus.newLogItem(item);
        }

        @Override
        public void updateStateString(String state, String msg, int resid, ConnectionStatus
                level, Intent intent) throws RemoteException {
            VpnStatus.updateStateString(state, msg, resid, level, intent);
        }

        @Override
        public void updateByteCount(long inBytes, long outBytes) throws RemoteException {
            VpnStatus.updateByteCount(inBytes, outBytes);
        }

        @Override
        public void connectedVPN(String uuid) throws RemoteException {
            VpnStatus.setConnectedVPNProfile(uuid);
        }
    };
    private final ServiceConnection mConnection = new ServiceConnection() {


        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            IServiceStatus serviceStatus = IServiceStatus.Stub.asInterface(service);
            try {
                /* Check if this a local service ... */
                if (service.queryLocalInterface("de.blinkt.openvpn.core.IServiceStatus") == null) {
                    // Not a local service
                    VpnStatus.setConnectedVPNProfile(serviceStatus.getLastConnectedVPN());
                    VpnStatus.setTrafficHistory(serviceStatus.getTrafficHistory());
                    ParcelFileDescriptor pfd = serviceStatus.registerStatusCallback(mCallback);
                    DataInputStream fd = new DataInputStream(new ParcelFileDescriptor.AutoCloseInputStream(pfd));

                    short len = fd.readShort();
                    byte[] buf = new byte[65336];
                    while (len != 0x7fff) {
                        fd.readFully(buf, 0, len);
                        LogItem logitem = new LogItem(buf, len);
                        VpnStatus.newLogItem(logitem, false);
                        len = fd.readShort();
                    }
                    fd.close();
                    pfd.close();


                } else {
                    VpnStatus.initLogCache(mCacheDir);
                    /* Set up logging to Logcat with a context) */

                    if (BuildConfig.DEBUG || BuildConfig.FLAVOR.equals("skeleton")) {
                        VpnStatus.addLogListener(StatusListener.this);
                    }


                }

            } catch (RemoteException | IOException e) {
                e.printStackTrace();
                VpnStatus.logException(e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            VpnStatus.removeLogListener(StatusListener.this);
        }

    };

    void init(Context c) {

        Intent intent = new Intent(c, OpenVPNStatusService.class);
        intent.setAction(OpenVPNService.START_SERVICE);
        mCacheDir = c.getCacheDir();

        c.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        this.mContext = c;

    }

    @Override
    public void newLog(LogItem logItem) {
        switch (logItem.getLogLevel()) {
            case INFO:
                Log.i("OpenVPN", logItem.getString(mContext));
                break;
            case DEBUG:
                Log.d("OpenVPN", logItem.getString(mContext));
                break;
            case ERROR:
                Log.e("OpenVPN", logItem.getString(mContext));
                break;
            case VERBOSE:
                Log.v("OpenVPN", logItem.getString(mContext));
                break;
            case WARNING:
            default:
                Log.w("OpenVPN", logItem.getString(mContext));
                break;
        }

    }
}
