/*
 * Copyright (c) 2012-2018 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

/*
 * Portions Copyright 2014-2016 Hans-Christoph Steiner
 * Portions Copyright 2012-2016 Nathan Freitas
 * Portions Copyright (c) 2016 CommonsWare, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package de.blinkt.openvpn.core;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.blinkt.openvpn.core.OpenVPNService.ORBOT_PACKAGE_NAME;

public class OrbotHelper {
    //! Based on the class from NetCipher but stripped down and modified for icsopenvpn

    /**
     * {@link Intent} send by Orbot with {@code ON/OFF/STARTING/STOPPING} status
     * included as an {@link #EXTRA_STATUS} {@code String}.  Your app should
     * always receive {@code ACTION_STATUS Intent}s since any other app could
     * start Orbot.  Also, user-triggered starts and stops will also cause
     * {@code ACTION_STATUS Intent}s to be broadcast.
     */
    public final static String ACTION_STATUS = "org.torproject.android.intent.action.STATUS";
    public final static String STATUS_ON = "ON";
    public final static String STATUS_STARTS_DISABLED = "STARTS_DISABLED";

    public final static String STATUS_STARTING = "STARTING";
    public final static String STATUS_STOPPING = "STOPPING";
    public final static String EXTRA_STATUS = "org.torproject.android.intent.extra.STATUS";
    /**
     * A request to Orbot to transparently start Tor services
     */
    public final static String ACTION_START = "org.torproject.android.intent.action.START";
    public final static String EXTRA_PACKAGE_NAME = "org.torproject.android.intent.extra.PACKAGE_NAME";
    public static final int SOCKS_PROXY_PORT_DEFAULT = 9050;
    private static OrbotHelper mInstance;

    String EXTRA_SOCKS_PROXY_HOST = "org.torproject.android.intent.extra.SOCKS_PROXY_HOST";
    String EXTRA_SOCKS_PROXY_PORT = "org.torproject.android.intent.extra.SOCKS_PROXY_PORT";
    private Context mContext;
    private Set<StatusCallback> statusCallbacks = new HashSet<>();
    private BroadcastReceiver orbotStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (TextUtils.equals(intent.getAction(),
                    OrbotHelper.ACTION_STATUS)) {
                for (StatusCallback cb : statusCallbacks) {
                    cb.onStatus(intent);
                }

                String status = intent.getStringExtra(EXTRA_STATUS);
                if (TextUtils.equals(status, STATUS_ON)) {
                    int socksPort = intent.getIntExtra(EXTRA_SOCKS_PROXY_PORT, SOCKS_PROXY_PORT_DEFAULT);
                    String socksHost = intent.getStringExtra(EXTRA_SOCKS_PROXY_HOST);
                    if (TextUtils.isEmpty(socksHost))
                        socksHost = "127.0.0.1";
                    for (StatusCallback cb : statusCallbacks) {
                        cb.onOrbotReady(intent, socksHost, socksPort);
                    }
                } else if (TextUtils.equals(status, STATUS_STARTS_DISABLED)) {
                    for (StatusCallback cb : statusCallbacks)
                        cb.onDisabled(intent);
                }

            }
        }
    };

    private OrbotHelper() {

    }

    public static OrbotHelper get(OpenVPNService mOpenVPNService) {
        if (mInstance == null)
            mInstance = new OrbotHelper();
        return mInstance;
    }

    /**
     * Gets an {@link Intent} for starting Orbot.  Orbot will reply with the
     * current status to the {@code packageName} of the app in the provided
     * {@link Context} (i.e.  {@link Context#getPackageName()}.
     */
    public static Intent getOrbotStartIntent(Context context) {
        Intent intent = new Intent(ACTION_START);
        intent.setPackage(ORBOT_PACKAGE_NAME);
        intent.putExtra(EXTRA_PACKAGE_NAME, context.getPackageName());
        return intent;
    }

    public static boolean checkTorReceier(Context c) {
        Intent startOrbot = getOrbotStartIntent(c);
        PackageManager pm = c.getPackageManager();
        Intent result = null;
        List<ResolveInfo> receivers =
                pm.queryBroadcastReceivers(startOrbot, 0);

        return receivers != null && receivers.size() > 0;
    }

    /**
     * Adds a StatusCallback to be called when we find out that
     * Orbot is ready. If Orbot is ready for use, your callback
     * will be called with onEnabled() immediately, before this
     * method returns.
     *
     * @param cb a callback
     * @return the singleton, for chaining
     */
    public synchronized OrbotHelper addStatusCallback(Context c, StatusCallback cb) {
        if (statusCallbacks.size() == 0) {
            c.getApplicationContext().registerReceiver(orbotStatusReceiver,
                    new IntentFilter(OrbotHelper.ACTION_STATUS));
            mContext = c.getApplicationContext();
        }
        if (!checkTorReceier(c))
            cb.onNotYetInstalled();
        statusCallbacks.add(cb);
        return (this);
    }

    /**
     * Removes an existing registered StatusCallback.
     *
     * @param cb the callback to remove
     * @return the singleton, for chaining
     */
    public synchronized void removeStatusCallback(StatusCallback cb) {
        statusCallbacks.remove(cb);
        if (statusCallbacks.size() == 0)
            mContext.unregisterReceiver(orbotStatusReceiver);
    }

    public void sendOrbotStartAndStatusBroadcast() {
        mContext.sendBroadcast(getOrbotStartIntent(mContext));
    }

    private void startOrbotService(String action) {
        Intent clearVPNMode = new Intent();
        clearVPNMode.setComponent(new ComponentName(ORBOT_PACKAGE_NAME, ".service.TorService"));
        clearVPNMode.setAction(action);
        mContext.startService(clearVPNMode);
    }

    public interface StatusCallback {
        /**
         * Called when Orbot is operational
         *
         * @param statusIntent an Intent containing information about
         *                     Orbot, including proxy ports
         */
        void onStatus(Intent statusIntent);


        /**
         * Called if Orbot is not yet installed. Usually, you handle
         * this by checking the return value from init() on OrbotInitializer
         * or calling isInstalled() on OrbotInitializer. However, if
         * you have need for it, if a callback is registered before
         * an init() call determines that Orbot is not installed, your
         * callback will be called with onNotYetInstalled().
         */
        void onNotYetInstalled();

        void onOrbotReady(Intent intent, String socksHost, int socksPort);

        /**
         * Called if Orbot background control is disabled.
         * @param intent the intent delivered
         */
        void onDisabled(Intent intent);
    }
}
