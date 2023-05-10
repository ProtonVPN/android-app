/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.RequiresApi;

import de.blinkt.openvpn.BuildConfig;
import de.blinkt.openvpn.core.VpnStatus.LogLevel;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by arne on 09.11.16.
 */

public class StatusListener implements VpnStatus.LogListener {
    private final IStatusCallbacks mCallback = new IStatusCallbacks.Stub() {
        @Override
        public void newLogItem(LogItem item) throws RemoteException {
            VpnStatus.newLogItem(item);
        }

        @Override
        public void updateStateString(String state, String msg, int resid, ConnectionStatus
                level, Intent intent) throws RemoteException {
            Intent newIntent = reCreateIntent(intent);
            VpnStatus.updateStateString(state, msg, resid, level, newIntent);
        }

        private Intent reCreateIntent(Intent intent) {
            /* To avoid UnsafeIntentLaunchViolation we recreate the intent that we passed
             * to ourselves via the AIDL interface */
            if (intent == null)
                return null;
            Intent newIntent = new Intent(intent.getAction(), intent.getData());
            if (intent.getExtras() != null)
                newIntent.putExtras(intent.getExtras());
            return newIntent;
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
    private File mCacheDir;
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
    private Context mContext;

    void init(Context c) {

        Intent intent = new Intent(c, OpenVPNStatusService.class);
        intent.setAction(OpenVPNService.START_SERVICE);
        mCacheDir = c.getCacheDir();

        c.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        this.mContext = c;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            logLatestExitReasons(c);
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void logLatestExitReasons(Context c) {
        ActivityManager activityManager = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        List<ApplicationExitInfo> exitReasons = activityManager.getHistoricalProcessExitReasons(null, 0, 5);
        ApplicationExitInfo lastguiexit = null;
        ApplicationExitInfo lastserviceexit = null;
        for (ApplicationExitInfo aei : exitReasons) {
            if (aei.getProcessName().endsWith(":openvpn")) {
                if (lastserviceexit == null || aei.getTimestamp() > lastserviceexit.getTimestamp())
                    lastserviceexit = aei;
            } else {
                if (lastguiexit == null || aei.getTimestamp() > lastguiexit.getTimestamp())
                    lastguiexit = aei;
            }
        }
        logExitNotification(lastserviceexit, "Last exit reason reported by Android for Service Process: ");
        logExitNotification(lastguiexit, "Last exit reason reported by Android for UI Process: ");

    }

    private void logExitNotification(ApplicationExitInfo aei, String s) {
        if (aei != null) {
            LogItem li = new LogItem(LogLevel.DEBUG, s + aei, aei.getTimestamp());
            VpnStatus.newLogItemIfUnique(li);
        }
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
