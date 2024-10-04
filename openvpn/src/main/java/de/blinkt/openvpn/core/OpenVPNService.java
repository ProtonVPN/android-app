/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static de.blinkt.openvpn.VpnProfile.EXTRA_PROFILEUUID;
import static de.blinkt.openvpn.VpnProfile.EXTRA_PROFILE_VERSION;
import static de.blinkt.openvpn.core.ConnectionStatus.LEVEL_CONNECTED;
import static de.blinkt.openvpn.core.ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT;
import static de.blinkt.openvpn.core.NetworkSpace.IpAddress;

import android.Manifest.permission;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.ProxyInfo;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.service.notification.StatusBarNotification;
import android.system.OsConstants;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.ExecutionException;

import de.blinkt.openvpn.LaunchVPN;
import de.blinkt.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.activities.DisconnectVPN;
import de.blinkt.openvpn.api.ExternalAppDatabase;
import de.blinkt.openvpn.core.VpnStatus.ByteCountListener;
import de.blinkt.openvpn.core.VpnStatus.StateListener;

public abstract class OpenVPNService extends VpnService implements StateListener, Callback, ByteCountListener, IOpenVPNServiceInternal {
    public static final String START_SERVICE = "de.blinkt.openvpn.START_SERVICE";
    public static final String START_SERVICE_STICKY = "de.blinkt.openvpn.START_SERVICE_STICKY";
    public static final String ALWAYS_SHOW_NOTIFICATION = "de.blinkt.openvpn.NOTIFICATION_ALWAYS_VISIBLE";

    public static final String EXTRA_DO_NOT_REPLACE_RUNNING_VPN = "de.blinkt.openvpn.DO_NOT_REPLACE_RUNNING_VPN";

    public static final String EXTRA_START_REASON = "de.blinkt.openvpn.startReason";

    public static final String DISCONNECT_VPN = "de.blinkt.openvpn.DISCONNECT_VPN";
    public static final String NOTIFICATION_CHANNEL_BG_ID = "com.protonvpn.android";
    public static final String NOTIFICATION_CHANNEL_NEWSTATUS_ID = "com.protonvpn.android";
    public static final String NOTIFICATION_CHANNEL_USERREQ_ID = "openvpn_userreq";

    public static final String VPNSERVICE_TUN = "vpnservice-tun";
    public final static String ORBOT_PACKAGE_NAME = "org.torproject.android";
    public static final String EXTRA_CHALLENGE_TXT = "de.blinkt.openvpn.core.CR_TEXT_CHALLENGE";
    public static final String EXTRA_CHALLENGE_OPENURL = "de.blinkt.openvpn.core.OPENURL_CHALLENGE";
    public static final String PAUSE_VPN = "de.blinkt.openvpn.PAUSE_VPN";
    private static final String RESUME_VPN = "de.blinkt.openvpn.RESUME_VPN";
    private static final int PRIORITY_MIN = -2;
    private static final int PRIORITY_DEFAULT = 0;
    private static final int PRIORITY_MAX = 2;
    private static boolean mNotificationAlwaysVisible = false;


    static class TunConfig {
        private final Vector<String> mDnslist = new Vector<>();
        private final NetworkSpace mRoutes = new NetworkSpace();
        private final NetworkSpace mRoutesv6 = new NetworkSpace();
        private Vector<String> mSearchDomainList = new Vector<>();
        private CIDRIP mLocalIP = null;
        private int mMtu;
        private String mLocalIPv6 = null;

        private ProxyInfo mProxyInfo;
    };

    private TunConfig tunConfig = new TunConfig();

    private final Object mProcessLock = new Object();
    private String lastChannel;
    private Thread mProcessThread = null;
    private VpnProfile mProfile;

    private DeviceStateReceiver mDeviceStateReceiver;
    private boolean mDisplayBytecount = false;
    private boolean mStarting = false;
    private long mConnecttime;
    private OpenVPNManagement mManagement;
    private final IBinder mBinder = new IOpenVPNServiceInternal.Stub() {

        @Override
        public boolean protect(int fd) throws RemoteException {
            return OpenVPNService.this.protect(fd);
        }

        @Override
        public void userPause(boolean shouldbePaused) throws RemoteException {
            OpenVPNService.this.userPause(shouldbePaused);
        }

        @Override
        public boolean stopVPN(boolean replaceConnection) throws RemoteException {
            return OpenVPNService.this.stopVPN(replaceConnection);
        }

        @Override
        public void addAllowedExternalApp(String packagename) throws RemoteException {
            OpenVPNService.this.addAllowedExternalApp(packagename);
        }

        @Override
        public boolean isAllowedExternalApp(String packagename) throws RemoteException {
            return OpenVPNService.this.isAllowedExternalApp(packagename);

        }

        @Override
        public void challengeResponse(String repsonse) throws RemoteException {
            OpenVPNService.this.challengeResponse(repsonse);
        }


    };
    private TunConfig mLastTunCfg;
    private String mRemoteGW;
    private Handler guiHandler;
    private Toast mlastToast;
    private Runnable mOpenVPNThread;
    private HandlerThread mCommandHandlerThread;
    private Handler mCommandHandler;

    private volatile boolean shouldRollbackConnection = false;

    public abstract VpnProfile getProfile();

    // Returns if service should keep running
    protected abstract boolean onProcessRestore();

    // From: http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
    public static String humanReadableByteCount(long bytes, boolean speed, Resources res) {
        if (speed)
            bytes = bytes * 8;
        int unit = speed ? 1000 : 1024;


        int exp = Math.max(0, Math.min((int) (Math.log(bytes) / Math.log(unit)), 3));

        float bytesUnit = (float) (bytes / Math.pow(unit, exp));

        if (speed)
            switch (exp) {
                case 0:
                    return res.getString(R.string.bits_per_second, bytesUnit);
                case 1:
                    return res.getString(R.string.kbits_per_second, bytesUnit);
                case 2:
                    return res.getString(R.string.mbits_per_second, bytesUnit);
                default:
                    return res.getString(R.string.gbits_per_second, bytesUnit);
            }
        else
            switch (exp) {
                case 0:
                    return res.getString(R.string.volume_byte, bytesUnit);
                case 1:
                    return res.getString(R.string.volume_kbyte, bytesUnit);
                case 2:
                    return res.getString(R.string.volume_mbyte, bytesUnit);
                default:
                    return res.getString(R.string.volume_gbyte, bytesUnit);

            }
    }



    @Override
    public void addAllowedExternalApp(String packagename) throws RemoteException {
        ExternalAppDatabase extapps = new ExternalAppDatabase(OpenVPNService.this);
        if(extapps.checkAllowingModifyingRemoteControl(this)) {
            extapps.addApp(packagename);
        }
    }

    @Override
    public boolean isAllowedExternalApp(String packagename) throws RemoteException {
        ExternalAppDatabase extapps = new ExternalAppDatabase(OpenVPNService.this);
        return extapps.checkRemoteActionPermission(this, packagename);
    }

    @Override
    public void challengeResponse(String response) throws RemoteException {
        if (mManagement != null) {
            String b64response = Base64.encodeToString(response.getBytes(Charset.forName("UTF-8")), Base64.NO_WRAP);
            mManagement.sendCRResponse(b64response);
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(START_SERVICE))
            return mBinder;
        else
            return super.onBind(intent);
    }

    @Override
    public void onRevoke() {
        VpnStatus.logError(R.string.permission_revoked);
        final OpenVPNManagement managment = mManagement;
        mCommandHandler.post(() -> managment.stopVPN(false));

        endVpnService();
    }

    // Similar to revoke but do not try to stop process
    public void openvpnStopped() {
        endVpnService();
    }

    private boolean isAlwaysActiveEnabled()
    {
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(this);
        return prefs.getBoolean("restartvpnonboot", false);
    }

    boolean isVpnAlwaysOnEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return isAlwaysOn();
        }
        return false;
    }


    private void endVpnService() {
        if (!isVpnAlwaysOnEnabled() && !isAlwaysActiveEnabled()) {
            /* if we should be an always on VPN, keep the timer running */
            //keepVPNAlive.unscheduleKeepVPNAliveJobService(this);
        }
        synchronized (mProcessLock) {
            mProcessThread = null;
        }
        VpnStatus.removeByteCountListener(this);
        unregisterDeviceStateReceiver(mDeviceStateReceiver);
        mDeviceStateReceiver = null;
        ProfileManager.setConntectedVpnProfileDisconnected(this);
        mOpenVPNThread = null;
        if (!mStarting) {
            // Notification will be removed in NotificationHelper. Calling stopForeground(true) causes
            // subsequent NotificationManager.cancel to fail.
            stopForeground(false);

            if (!mNotificationAlwaysVisible) {
                stopSelf();
                VpnStatus.removeStateListener(this);
            }
        }
    }

    private void showNotification(final String msg, String tickerText, @NonNull String channel,
                                  long when, ConnectionStatus status, Intent intent) {
    /*    NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        int icon = getIconByConnectionStatus(status);

        android.app.Notification.Builder nbuilder = new Notification.Builder(this);

        int priority;
        if (channel.equals(NOTIFICATION_CHANNEL_BG_ID))
            priority = PRIORITY_MIN;
        else if (channel.equals(NOTIFICATION_CHANNEL_USERREQ_ID))
            priority = PRIORITY_MAX;
        else
            priority = PRIORITY_DEFAULT;

        if (mProfile != null)
            nbuilder.setContentTitle(getString(R.string.notifcation_title, mProfile.mName));
        else
            nbuilder.setContentTitle(getString(R.string.notifcation_title_notconnect));

        nbuilder.setContentText(msg);
        nbuilder.setOnlyAlertOnce(true);
        nbuilder.setOngoing(true);

        nbuilder.setSmallIcon(icon);
        if (status == LEVEL_WAITING_FOR_USER_INPUT && intent != null) {
            PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            nbuilder.setContentIntent(pIntent);
        } else {
            nbuilder.setContentIntent(getGraphPendingIntent());
        }

        if (when != 0)
            nbuilder.setWhen(when);


        // Try to set the priority available since API 16 (Jellybean)
        jbNotificationExtras(priority, nbuilder);
        addVpnActionsToNotification(nbuilder);
        lpNotificationExtras(nbuilder, Notification.CATEGORY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //noinspection NewApi
            nbuilder.setChannelId(channel);
            if (mProfile != null)
                //noinspection NewApi
                nbuilder.setShortcutId(mProfile.getUUIDString());

        }

        if (tickerText != null && !tickerText.equals(""))
            nbuilder.setTicker(tickerText);

        @SuppressWarnings("deprecation")
        Notification notification = nbuilder.getNotification();

        int notificationId = channel.hashCode();

        mNotificationManager.notify(notificationId, notification);

        startForeground(notificationId, notification);

        if (lastChannel != null && !channel.equals(lastChannel)) {
            // Cancel old notification
            mNotificationManager.cancel(lastChannel.hashCode());
        }

        // Check if running on a TV
        if (runningOnAndroidTV() && !(priority < 0))
            guiHandler.post(() -> {
                if (mlastToast != null)
                    mlastToast.cancel();
                String name;
                if (mProfile != null)
                    name = mProfile.mName;
                else
                    name = "OpenVPN";
                String toastText = String.format(Locale.getDefault(), "%s - %s", name, msg);
                mlastToast = Toast.makeText(getBaseContext(), toastText, Toast.LENGTH_SHORT);
                mlastToast.show();
            }); */
    }

    private void lpNotificationExtras(Notification.Builder nbuilder, String category) {
        nbuilder.setCategory(category);
        nbuilder.setLocalOnly(true);

    }

    private boolean runningOnAndroidTV() {
        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private int getIconByConnectionStatus(ConnectionStatus level) {
        switch (level) {
            case LEVEL_CONNECTED:
                return R.drawable.ic_stat_vpn;
            case LEVEL_AUTH_FAILED:
            case LEVEL_NONETWORK:
            case LEVEL_NOTCONNECTED:
                return R.drawable.ic_stat_vpn_offline;
            case LEVEL_CONNECTING_NO_SERVER_REPLY_YET:
            case LEVEL_WAITING_FOR_USER_INPUT:
                return R.drawable.ic_stat_vpn_outline;
            case LEVEL_CONNECTING_SERVER_REPLIED:
                return R.drawable.ic_stat_vpn_empty_halo;
            case LEVEL_VPNPAUSED:
                return android.R.drawable.ic_media_pause;
            case UNKNOWN_LEVEL:
            default:
                return R.drawable.ic_stat_vpn;

        }
    }

    private void jbNotificationExtras(int priority,
                                      android.app.Notification.Builder nbuilder) {
        try {
            if (priority != 0) {
                Method setpriority = nbuilder.getClass().getMethod("setPriority", int.class);
                setpriority.invoke(nbuilder, priority);

                Method setUsesChronometer = nbuilder.getClass().getMethod("setUsesChronometer", boolean.class);
                setUsesChronometer.invoke(nbuilder, true);

            }

            //ignore exception
        } catch (NoSuchMethodException | IllegalArgumentException |
                InvocationTargetException | IllegalAccessException e) {
            VpnStatus.logException(e);
        }

    }

    private void addVpnActionsToNotification(Notification.Builder nbuilder) {
        Intent disconnectVPN = new Intent(this, DisconnectVPN.class);
        disconnectVPN.setAction(DISCONNECT_VPN);
        PendingIntent disconnectPendingIntent = PendingIntent.getActivity(this, 0, disconnectVPN, PendingIntent.FLAG_IMMUTABLE);

        nbuilder.addAction(R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.cancel_connection), disconnectPendingIntent);

        Intent pauseVPN = new Intent(this, OpenVPNService.class);
        if (mDeviceStateReceiver == null || !mDeviceStateReceiver.isUserPaused()) {
            pauseVPN.setAction(PAUSE_VPN);
            PendingIntent pauseVPNPending = PendingIntent.getService(this, 0, pauseVPN, PendingIntent.FLAG_IMMUTABLE);
            nbuilder.addAction(R.drawable.ic_menu_pause,
                    getString(R.string.pauseVPN), pauseVPNPending);

        } else {
            pauseVPN.setAction(RESUME_VPN);
            PendingIntent resumeVPNPending = PendingIntent.getService(this, 0, pauseVPN, PendingIntent.FLAG_IMMUTABLE);
            nbuilder.addAction(R.drawable.ic_menu_play,
                    getString(R.string.resumevpn), resumeVPNPending);
        }
    }

    PendingIntent getUserInputIntent(String needed) {
        Intent intent = new Intent(getApplicationContext(), LaunchVPN.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.putExtra("need", needed);
        Bundle b = new Bundle();
        b.putString("need", needed);
        PendingIntent pIntent = PendingIntent.getActivity(this, 12, intent, PendingIntent.FLAG_IMMUTABLE);
        return pIntent;
    }

    PendingIntent getGraphPendingIntent() {
        // Let the configure Button show the Log


        Intent intent = new Intent();
        intent.setComponent(new ComponentName(this, getPackageName() + ".activities.MainActivity"));

        intent.putExtra("PAGE", "graph");
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent startLW = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return startLW;

    }

    synchronized void registerDeviceStateReceiver(DeviceStateReceiver newDeviceStateReceiver) {
        // Registers BroadcastReceiver to track network connection changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);

        // Fetch initial network state
        newDeviceStateReceiver.networkStateChange(this);

        registerReceiver(newDeviceStateReceiver, filter);
        VpnStatus.addByteCountListener(newDeviceStateReceiver);
    }

    synchronized void unregisterDeviceStateReceiver(DeviceStateReceiver deviceStateReceiver) {
        if (mDeviceStateReceiver != null)
            try {
                VpnStatus.removeByteCountListener(deviceStateReceiver);
                this.unregisterReceiver(deviceStateReceiver);
            } catch (IllegalArgumentException iae) {
                // I don't know why  this happens:
                // java.lang.IllegalArgumentException: Receiver not registered: de.blinkt.openvpn.NetworkSateReceiver@41a61a10
                // Ignore for now ...
                iae.printStackTrace();
            }
    }

    public void userPause(boolean shouldBePaused) {
        if (mDeviceStateReceiver != null)
            mDeviceStateReceiver.userPause(shouldBePaused);
    }

    @Override
    public boolean stopVPN(boolean replaceConnection) throws RemoteException {
        if (getManagement() != null)
            return getManagement().stopVPN(replaceConnection);
        else
            return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra(ALWAYS_SHOW_NOTIFICATION, false))
            mNotificationAlwaysVisible = true;

        VpnStatus.addStateListener(this);
        VpnStatus.addByteCountListener(this);

        if (intent == null) {
            // Process restart
            boolean shouldStart = onProcessRestore();
            if (!shouldStart) {
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }

        if (intent != null && PAUSE_VPN.equals(intent.getAction())) {
            // This value will be picked up by any ongoing startOpenVPN execution in background
            // thread so that it can be properly rolled back.
            shouldRollbackConnection = true;
            try {
                stopVPN(false);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            return START_NOT_STICKY;
        }

        shouldRollbackConnection = false;

        if (intent != null && RESUME_VPN.equals(intent.getAction())) {
            if (mDeviceStateReceiver != null)
                mDeviceStateReceiver.userPause(false);
            return START_NOT_STICKY;
        }


        if (intent != null && START_SERVICE.equals(intent.getAction()))
            return START_NOT_STICKY;
        if (intent != null && START_SERVICE_STICKY.equals(intent.getAction())) {
            return START_REDELIVER_INTENT;
        }


        // Always show notification here to avoid problem with startForeground timeout
        VpnStatus.logInfo(R.string.building_configration);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M  || (!foregroundNotificationVisible())) {

            VpnStatus.updateStateString("VPN_GENERATE_CONFIG", "", R.string.building_configration, ConnectionStatus.LEVEL_START);
            showNotification(VpnStatus.getLastCleanLogMessage(this),
                    VpnStatus.getLastCleanLogMessage(this), NOTIFICATION_CHANNEL_NEWSTATUS_ID, 0, ConnectionStatus.LEVEL_START, null);
        }
        shouldRollbackConnection = false;
        /* start the OpenVPN process itself in a background thread */
        mCommandHandler.post(() -> startOpenVPN(intent, startId));

        return START_STICKY;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean foregroundNotificationVisible() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        StatusBarNotification[] notifications = mNotificationManager.getActiveNotifications();
        /* Assume for simplicity that all our notifications are foreground */
        return notifications.length > 0;
    }

    private void rollbackConnection() {
        VpnStatus.updateStateString("NOPROCESS", "", 0, ConnectionStatus.LEVEL_NOTCONNECTED);
        guiHandler.post(() -> {
            try {
                stopVPN(false);
                endVpnService();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private void updateShortCutUsage(VpnProfile profile) {
        if (profile == null)
            return;
        ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        if (shortcutManager!=null) {
            /* This should never been null but I do not trust Android ROMs to do the right thing
             * anymore and neither seems Coverity */
            shortcutManager.reportShortcutUsed(profile.getUUIDString());
        }
    }

    private VpnProfile fetchVPNProfile(Intent intent)
    {
        VpnProfile vpnProfile = null;
        String startReason;
        if (intent != null && intent.hasExtra(EXTRA_PROFILEUUID)) {
            String profileUUID = intent.getStringExtra(EXTRA_PROFILEUUID);
            int profileVersion = intent.getIntExtra(EXTRA_PROFILE_VERSION, 0);
            startReason = intent.getStringExtra(EXTRA_START_REASON);
            if (startReason == null)
                startReason = "(unknown)";
            // Try for 10s to get current version of the profile
            vpnProfile = getProfile();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                updateShortCutUsage(vpnProfile);
            }

        } else {
            /* The intent is null when we are set as always-on or the service has been restarted. */
            vpnProfile = getProfile();
            startReason = "Using last connected profile (started with null intent, always-on or restart after crash)";
            VpnStatus.logInfo(R.string.service_restarted);

            /* Got no profile, just stop */
            if (vpnProfile == null) {
                startReason = "could not get last connected profile, using default (started with null intent, always-on or restart after crash)";

                Log.d("OpenVPN", "Got no last connected profile on null intent. Assuming always on.");
                vpnProfile = ProfileManager.getAlwaysOnVPN(this);


                if (vpnProfile == null) {
                    return null;
                }
            }
            /* Do the asynchronous keychain certificate stuff */
            vpnProfile.checkForRestart(this);
        }
        String name = "(null)";
        if (vpnProfile != null)
            name = vpnProfile.getName();
        VpnStatus.logDebug(String.format("Fetched VPN profile (%s) triggered by %s", name, startReason));
        return vpnProfile;
    }

    private boolean checkVPNPermission(VpnProfile startprofile) {
        if (prepare(this) == null)
            return true;

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Notification.Builder nbuilder = new Notification.Builder(this);
        nbuilder.setAutoCancel(true);
        int icon = android.R.drawable.ic_dialog_info;
        nbuilder.setSmallIcon(icon);

        Intent launchVPNIntent = new Intent(this, LaunchVPN.class);
        launchVPNIntent.putExtra(LaunchVPN.EXTRA_KEY, startprofile.getUUIDString());
        launchVPNIntent.putExtra(EXTRA_START_REASON, "OpenService lacks permission");
        launchVPNIntent.putExtra(de.blinkt.openvpn.LaunchVPN.EXTRA_HIDELOG, true);
        launchVPNIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        launchVPNIntent.setAction(Intent.ACTION_MAIN);


        showNotification(getString(R.string.permission_requested),
                "", NOTIFICATION_CHANNEL_USERREQ_ID, 0, LEVEL_WAITING_FOR_USER_INPUT, launchVPNIntent);

        VpnStatus.updateStateString("USER_INPUT", "waiting for user input", R.string.permission_requested, LEVEL_WAITING_FOR_USER_INPUT, launchVPNIntent);
        return false;
    }



    private void startOpenVPN(Intent intent, int startId) {
        if (shouldRollbackConnection) {
            rollbackConnection();
            return;
        }

        VpnProfile vp = fetchVPNProfile(intent);
        if (vp == null) {
            stopSelf(startId);
            return;
        }

        if (!checkVPNPermission(vp))
            return;

        boolean noReplaceRequested =  (intent != null) && intent.getBooleanExtra(EXTRA_DO_NOT_REPLACE_RUNNING_VPN, false);


        /* we get an empty start request or explicitly get told to not replace the VPN then ignore
         * a start request. This avoids OnBootreciver, Always and user quickly clicking to have
         * weird race conditions
         */
        if (mProfile != null && mProfile == vp && (intent == null || noReplaceRequested))
        {
            /* we do not want to replace the running VPN */
            VpnStatus.logInfo(R.string.ignore_vpn_start_request, mProfile.getName());
            return;
        }

        mProfile = vp;
        ProfileManager.setConnectedVpnProfile(this, vp);
        VpnStatus.setConnectedVPNProfile(vp.getUUIDString());
        //keepVPNAlive.scheduleKeepVPNAliveJobService(this, vp);

        String nativeLibraryDirectory = getApplicationInfo().nativeLibraryDir;
        String tmpDir;
        try {
            tmpDir = getApplication().getCacheDir().getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
            tmpDir = "/tmp";
        }

        // Write OpenVPN binary
        String[] argv = VPNLaunchHelper.buildOpenvpnArgv(this);


        // Set a flag that we are starting a new VPN
        mStarting = true;
        // Stop the previous session by interrupting the thread.
        stopOldOpenVPNProcess(mManagement, mOpenVPNThread);
        // An old running VPN should now be exited
        mStarting = false;

        if (shouldRollbackConnection) {
            rollbackConnection();
            return;
        }

        // Start a new session by creating a new thread.
        boolean useOpenVPN3 = VpnProfile.doUseOpenVPN3(this);

        // Open the Management Interface
        if (!useOpenVPN3) {
            // start a Thread that handles incoming messages of the management socket
            OpenVpnManagementThread ovpnManagementThread = new OpenVpnManagementThread(mProfile, this);
            if (ovpnManagementThread.openManagementInterface(this)) {
                Thread mSocketManagerThread = new Thread(ovpnManagementThread, "OpenVPNManagementThread");
                mSocketManagerThread.start();
                mManagement = ovpnManagementThread;
                VpnStatus.logInfo("started Socket Thread");
            } else {
                endVpnService();
                return;
            }
        }

        Runnable processThread;
        if (useOpenVPN3) {
            OpenVPNManagement mOpenVPN3 = instantiateOpenVPN3Core();
            processThread = (Runnable) mOpenVPN3;
            mManagement = mOpenVPN3;
        } else {
            processThread = new OpenVPNThread(this, argv, nativeLibraryDirectory, tmpDir);
        }

        synchronized (mProcessLock) {
            mProcessThread = new Thread(processThread, "OpenVPNProcessThread");
            mProcessThread.start();
        }

        if (!useOpenVPN3) {
            try {
                mProfile.writeConfigFileOutput(this, ((OpenVPNThread) processThread).getOpenVPNStdin());
            } catch (IOException | ExecutionException | InterruptedException e) {
                VpnStatus.logException("Error generating config file", e);
                endVpnService();
                return;
            }
        }

        final DeviceStateReceiver oldDeviceStateReceiver = mDeviceStateReceiver;
        final DeviceStateReceiver newDeviceStateReceiver = new DeviceStateReceiver(mManagement);

        guiHandler.post(() -> {
            if (oldDeviceStateReceiver != null)
                unregisterDeviceStateReceiver(oldDeviceStateReceiver);

            registerDeviceStateReceiver(newDeviceStateReceiver);
            mDeviceStateReceiver = newDeviceStateReceiver;
        });
    }


    private void stopOldOpenVPNProcess(OpenVPNManagement management,
                                       Runnable mamanagmentThread) {
        if (management != null) {
            if (mamanagmentThread != null)
                ((OpenVPNThread) mamanagmentThread).setReplaceConnection();
            if (management.stopVPN(true)) {
                // an old was asked to exit, wait 1s
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        }

        forceStopOpenVpnProcess();
    }

    public void forceStopOpenVpnProcess() {
        synchronized (mProcessLock) {
            if (mProcessThread != null) {
                mProcessThread.interrupt();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        }
    }

    private OpenVPNManagement instantiateOpenVPN3Core() {
        try {
            Class<?> cl = Class.forName("de.blinkt.openvpn.core.OpenVPNThreadv3");
            return (OpenVPNManagement) cl.getConstructor(OpenVPNService.class, VpnProfile.class).newInstance(this, mProfile);
        } catch (IllegalArgumentException | InstantiationException | InvocationTargetException |
                NoSuchMethodException | ClassNotFoundException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }


    @Override
    public IBinder asBinder() {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        guiHandler = new Handler(getMainLooper());
        mCommandHandlerThread = new HandlerThread("OpenVPNServiceCommandThread");
        mCommandHandlerThread.start();
        mCommandHandler = new Handler(mCommandHandlerThread.getLooper());
    }

    @Override
    public void onDestroy() {
        synchronized (mProcessLock) {
            if (mProcessThread != null) {
                mManagement.stopVPN(true);
            }
        }

        if (mDeviceStateReceiver != null) {
            unregisterDeviceStateReceiver(mDeviceStateReceiver);
            mDeviceStateReceiver = null;
        }
        // Just in case unregister for state
        VpnStatus.removeStateListener(this);
        VpnStatus.flushLog();
    }

    private static String getTunConfigString(TunConfig tc) {
        // The format of the string is not important, only that
        // two identical configurations produce the same result
        if (tc == null)
            return "NULL";

        String cfg = "TUNCFG UNQIUE STRING ips:";

        if (tc.mLocalIP != null)
            cfg += tc.mLocalIP.toString();
        if (tc.mLocalIPv6 != null)
            cfg += tc.mLocalIPv6;


        cfg += "routes: " + TextUtils.join("|", tc.mRoutes.getNetworks(true)) + TextUtils.join("|", tc.mRoutesv6.getNetworks(true));
        cfg += "excl. routes:" + TextUtils.join("|", tc.mRoutes.getNetworks(false)) + TextUtils.join("|", tc.mRoutesv6.getNetworks(false));
        cfg += "dns: " + TextUtils.join("|", tc.mDnslist);
        cfg += "domain: " + TextUtils.join("|", tc.mSearchDomainList);
        cfg += "mtu: " + tc.mMtu;
        cfg += "proxyInfo: " + tc.mProxyInfo;
        return cfg;
    }

    public ParcelFileDescriptor openTun() {
        ParcelFileDescriptor pfd = openTun(tunConfig);

        // Reset information
        mLastTunCfg = tunConfig;
        tunConfig = new TunConfig();
        return pfd;
    }
    private ParcelFileDescriptor openTun(TunConfig tc) {

        //Debug.startMethodTracing(getExternalFilesDir(null).toString() + "/opentun.trace", 40* 1024 * 1024);

        Builder builder = new Builder();

        VpnStatus.logInfo(R.string.last_openvpn_tun_config);

        if (mProfile == null)
        {
            VpnStatus.logError("OpenVPN tries to open a VPN descriptor with mProfile==null, please report this bug with log!");
            return null;
        }

        boolean allowUnsetAF = !mProfile.mBlockUnusedAddressFamilies;
        if (allowUnsetAF) {
            allowAllAFFamilies(builder);
        }

        if (tc.mLocalIP == null && tc.mLocalIPv6 == null) {
            VpnStatus.logError(getString(R.string.opentun_no_ipaddr));
            return null;
        }

        if (tc.mLocalIP != null) {
            // OpenVPN3 manages excluded local networks by callback
            if (!VpnProfile.doUseOpenVPN3(this))
                addLocalNetworksToRoutes(tc);
            try {
                builder.addAddress(tc.mLocalIP.mIp, tc.mLocalIP.len);
            } catch (IllegalArgumentException iae) {
                VpnStatus.logError(R.string.dns_add_error, tc.mLocalIP, iae.getLocalizedMessage());
                return null;
            }
        }

        if (tc.mLocalIPv6 != null) {
            String[] ipv6parts = tc.mLocalIPv6.split("/");
            try {
                builder.addAddress(ipv6parts[0], Integer.parseInt(ipv6parts[1]));
            } catch (IllegalArgumentException iae) {
                VpnStatus.logError(R.string.ip_add_error, tc.mLocalIPv6, iae.getLocalizedMessage());
                return null;
            }

        }


        for (String dns : tc.mDnslist) {
            try {
                builder.addDnsServer(dns);
            } catch (IllegalArgumentException iae) {
                VpnStatus.logError(R.string.dns_add_error, dns, iae.getLocalizedMessage());
            }
        }

        String release = Build.VERSION.RELEASE;
        builder.setMtu(tc.mMtu);

        Collection<IpAddress> positiveIPv4Routes = tc.mRoutes.getPositiveIPList();
        Collection<IpAddress> positiveIPv6Routes = tc.mRoutesv6.getPositiveIPList();

        if ("samsung".equals(Build.BRAND) && tc.mDnslist.size() >= 1) {
            // Check if the first DNS Server is in the VPN range
            try {
                IpAddress dnsServer = new IpAddress(new CIDRIP(tc.mDnslist.get(0), 32), true);
                boolean dnsIncluded = false;
                for (IpAddress net : positiveIPv4Routes) {
                    if (net.containsNet(dnsServer)) {
                        dnsIncluded = true;
                    }
                }
                if (!dnsIncluded) {
                    String samsungwarning = String.format("Warning Samsung Android 5.0+ devices ignore DNS servers outside the VPN range. To enable DNS resolution a route to your DNS Server (%s) has been added.", tc.mDnslist.get(0));
                    VpnStatus.logWarning(samsungwarning);
                    positiveIPv4Routes.add(dnsServer);
                }
            } catch (Exception e) {
                // If it looks like IPv6 ignore error
                if (!tc.mDnslist.get(0).contains(":"))
                    VpnStatus.logError("Error parsing DNS Server IP: " + tc.mDnslist.get(0));
            }
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            installRoutesExcluded(builder, tc.mRoutes);
            installRoutesExcluded(builder, tc.mRoutesv6);
        } else {
            installRoutesPostiveOnly(builder, positiveIPv4Routes, positiveIPv6Routes);
        }


        for (String domain: tc.mSearchDomainList)
            builder.addSearchDomain(domain);

        String ipv4info;
        String ipv6info;
        if (allowUnsetAF) {
            ipv4info = "(not set, allowed)";
            ipv6info = "(not set, allowed)";
        } else {
            ipv4info = "(not set)";
            ipv6info = "(not set)";
        }

        int ipv4len;
        if (tc.mLocalIP != null) {
            ipv4len = tc.mLocalIP.len;
            ipv4info = tc.mLocalIP.mIp;
        } else {
            ipv4len = -1;
        }

        if (tc.mLocalIPv6 != null) {
            ipv6info = tc.mLocalIPv6;
        }

        if ((!tc.mRoutes.getNetworks(false).isEmpty() || !tc.mRoutesv6.getNetworks(false).isEmpty()) && isLockdownEnabledCompat()) {
            VpnStatus.logInfo("VPN lockdown enabled (do not allow apps to bypass VPN) enabled. Route exclusion will not allow apps to bypass VPN (e.g. bypass VPN for local networks)");
        }

        VpnStatus.logInfo(R.string.local_ip_info, ipv4info, ipv4len, ipv6info, tc.mMtu);
        VpnStatus.logInfo(R.string.dns_server_info, TextUtils.join(", ", tc.mDnslist), tc.mSearchDomainList);
        VpnStatus.logInfo(R.string.routes_info_incl, TextUtils.join(", ", tc.mRoutes.getNetworks(true)), TextUtils.join(", ", tc.mRoutesv6.getNetworks(true)));
        VpnStatus.logInfo(R.string.routes_info_excl, TextUtils.join(", ", tc.mRoutes.getNetworks(false)), TextUtils.join(", ", tc.mRoutesv6.getNetworks(false)));
        if (tc.mProxyInfo != null) {
            VpnStatus.logInfo(R.string.proxy_info, tc.mProxyInfo.getHost(), tc.mProxyInfo.getPort());
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            /* On Tiramisu we install the routes exactly like promised */
            VpnStatus.logDebug(R.string.routes_debug, TextUtils.join(", ", positiveIPv4Routes), TextUtils.join(", ", positiveIPv6Routes));
        }
        //VpnStatus.logInfo(String.format("Always active %s", isAlwaysOn() ? "on" : "off"));

        setAllowedVpnPackages(builder);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            // VPN always uses the default network
            builder.setUnderlyingNetworks(null);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Setting this false, will cause the VPN to inherit the underlying network metered
            // value
            builder.setMetered(false);
        }

        String session = mProfile.mName;
        if (tc.mLocalIP != null && tc.mLocalIPv6 != null)
            session = getString(R.string.session_ipv6string, session, tc.mLocalIP, tc.mLocalIPv6);
        else if (tc.mLocalIP != null)
            session = getString(R.string.session_ipv4string, session, tc.mLocalIP);
        else
            session = getString(R.string.session_ipv4string, session, tc.mLocalIPv6);

        builder.setSession(session);

        // No DNS Server, log a warning
        if (tc.mDnslist.size() == 0)
            VpnStatus.logInfo(R.string.warn_no_dns);

        setHttpProxy(builder, tc);

        builder.setConfigureIntent(getGraphPendingIntent());

        try {
            //Debug.stopMethodTracing();
            ParcelFileDescriptor tun = builder.establish();
            if (tun == null)
                throw new NullPointerException("Android establish() method returned null (Really broken network configuration?)");
            return tun;
        } catch (Exception e) {
            if (e instanceof SecurityException && e.getMessage().contains("INTERACT_ACROSS_USERS")) {
                VpnStatus.updateStateString("MULTI_USER_PERMISSION", "", R.string.state_user_vpn_permission, ConnectionStatus.LEVEL_MULTI_USER_PERMISSION);
            }
            VpnStatus.logError(R.string.tun_open_error);
            VpnStatus.logError(getString(R.string.error) + e.getLocalizedMessage());
            return null;
        }

    }

    private void installRoutesExcluded(Builder builder, NetworkSpace routes)
    {
        for(IpAddress ipIncl: routes.getNetworks(true))
        {
            try {
                builder.addRoute(ipIncl.getPrefix());
            } catch (UnknownHostException|IllegalArgumentException ia) {
                VpnStatus.logError(getString(R.string.route_rejected) + ipIncl + " " + ia.getLocalizedMessage());
            }
        }
        for(IpAddress ipExcl: routes.getNetworks(false))
        {
            try {
                builder.excludeRoute(ipExcl.getPrefix());
            } catch (UnknownHostException|IllegalArgumentException ia) {
                VpnStatus.logError(getString(R.string.route_rejected) + ipExcl + " " + ia.getLocalizedMessage());
            }
        }
    }

    private void installRoutesPostiveOnly(Builder builder, Collection<IpAddress> positiveIPv4Routes, Collection<IpAddress> positiveIPv6Routes) {
        IpAddress multicastRange = new IpAddress(new CIDRIP("224.0.0.0", 3), true);

        for (IpAddress route : positiveIPv4Routes) {
            try {
                if (multicastRange.containsNet(route))
                    VpnStatus.logDebug(R.string.ignore_multicast_route, route.toString());
                else
                    builder.addRoute(route.getIPv4Address(), route.networkMask);
            } catch (IllegalArgumentException ia) {
                VpnStatus.logError(getString(R.string.route_rejected) + route + " " + ia.getLocalizedMessage());
            }
        }

        for (IpAddress route6 : positiveIPv6Routes) {
            try {
                builder.addRoute(route6.getIPv6Address(), route6.networkMask);
            } catch (IllegalArgumentException ia) {
                VpnStatus.logError(getString(R.string.route_rejected) + route6 + " " + ia.getLocalizedMessage());
            }
        }
    }

    private void setHttpProxy(Builder builder, TunConfig tc) {
        if (tc.mProxyInfo != null && Build.VERSION.SDK_INT >= 29) {
            builder.setHttpProxy(tc.mProxyInfo);
        } else if (tc.mProxyInfo != null) {
            VpnStatus.logWarning("HTTP Proxy needs Android 10 or later.");
        }
    }

    private boolean isLockdownEnabledCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return isLockdownEnabled();
        } else {
            /* We cannot determine this, return false */
            return false;
        }

    }

    private void allowAllAFFamilies(Builder builder) {
        builder.allowFamily(OsConstants.AF_INET);
        builder.allowFamily(OsConstants.AF_INET6);
    }

    private void addLocalNetworksToRoutes(TunConfig tc) {
        for (String net : NetworkUtils.getLocalNetworks(this, false)) {
            String[] netparts = net.split("/");
            String ipAddr = netparts[0];
            int netMask = Integer.parseInt(netparts[1]);
            if (ipAddr.equals(tc.mLocalIP.mIp))
                continue;

            if(mProfile.mAllowLocalLAN)
                tc.mRoutes.addIP(new CIDRIP(ipAddr, netMask), false);
        }

        if (mProfile.mAllowLocalLAN) {
            for (String net : NetworkUtils.getLocalNetworks(this, true)) {
                addRoutev6(net, false);
                ;
            }
        }
    }

    private void setAllowedVpnPackages(Builder builder) {
        boolean profileUsesOrBot = false;

        for (Connection c : mProfile.mConnections) {
            if (c.mProxyType == Connection.ProxyType.ORBOT)
                profileUsesOrBot = true;
        }

        if (profileUsesOrBot)
            VpnStatus.logDebug("VPN Profile uses at least one server entry with Orbot. Setting up VPN so that OrBot is not redirected over VPN.");


        boolean atLeastOneAllowedApp = false;

        if (mProfile.mAllowedAppsVpnAreDisallowed && profileUsesOrBot) {
            try {
                builder.addDisallowedApplication(ORBOT_PACKAGE_NAME);
            } catch (PackageManager.NameNotFoundException e) {
                VpnStatus.logDebug("Orbot not installed?");
            }
        }

        for (String pkg : mProfile.mAllowedAppsVpn) {
            try {
                if (mProfile.mAllowedAppsVpnAreDisallowed) {
                    builder.addDisallowedApplication(pkg);
                } else {
                    if (!(profileUsesOrBot && pkg.equals(ORBOT_PACKAGE_NAME))) {
                        builder.addAllowedApplication(pkg);
                        atLeastOneAllowedApp = true;
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                mProfile.mAllowedAppsVpn.remove(pkg);
                VpnStatus.logInfo(R.string.app_no_longer_exists, pkg);
            }
        }

        if (!mProfile.mAllowedAppsVpnAreDisallowed && !atLeastOneAllowedApp) {
            VpnStatus.logDebug(R.string.no_allowed_app, getPackageName());
            try {
                builder.addAllowedApplication(getPackageName());
            } catch (PackageManager.NameNotFoundException e) {
                VpnStatus.logError("This should not happen: " + e.getLocalizedMessage());
            }
        }

        if (mProfile.mAllowedAppsVpnAreDisallowed) {
            VpnStatus.logDebug(R.string.disallowed_vpn_apps_info, TextUtils.join(", ", mProfile.mAllowedAppsVpn));
        } else {
            VpnStatus.logDebug(R.string.allowed_vpn_apps_info, TextUtils.join(", ", mProfile.mAllowedAppsVpn));
        }

        if (mProfile.mAllowAppVpnBypass) {
            builder.allowBypass();
            VpnStatus.logDebug("Apps may bypass VPN");
        }
    }

    public void addDNS(String dns) {
        tunConfig.mDnslist.add(dns);
    }

    public void addDNS(String dns, int port) {
        if (port != 0 && port != 53)
        {
            VpnStatus.logInfo(R.string.dnsserver_ignore_port, port, dns);
        }
        tunConfig.mDnslist.add(dns);
    }


    public void addSearchDomain(String domain) {
        tunConfig.mSearchDomainList.add(domain);
    }

    /**
     * Route that is always included, used by the v3 core
     */
    public void addRoute(CIDRIP route, boolean include) {
        tunConfig.mRoutes.addIP(route, include);
    }

    public boolean addHttpProxy(String proxy, int port) {
        try {
            tunConfig.mProxyInfo = ProxyInfo.buildDirectProxy(proxy, port);
        } catch (Exception e) {
            VpnStatus.logError("Could not set proxy" + e.getLocalizedMessage());
            return false;
        }
        return true;
    }

    public void addRoute(String dest, String mask, String gateway, String device) {
        CIDRIP route = new CIDRIP(dest, mask);
        boolean include = isAndroidTunDevice(device);

        IpAddress gatewayIP = new IpAddress(new CIDRIP(gateway, 32), false);

        if (tunConfig.mLocalIP == null) {
            VpnStatus.logError("Local IP address unset and received. Neither pushed server config nor local config specifies an IP addresses. Opening tun device is most likely going to fail.");
            return;
        }
        IpAddress localNet = new IpAddress(tunConfig.mLocalIP, true);
        if (localNet.containsNet(gatewayIP))
            include = true;

        if (gateway != null &&
                (gateway.equals("255.255.255.255") || gateway.equals(mRemoteGW)))
            include = true;


        if (route.len == 32 && !mask.equals("255.255.255.255")) {
            VpnStatus.logWarning(R.string.route_not_cidr, dest, mask);
        }

        if (route.normalise())
            VpnStatus.logWarning(R.string.route_not_netip, dest, route.len, route.mIp);

        tunConfig.mRoutes.addIP(route, include);
    }

    public void addRoutev6(String network, String device) {
        // Tun is opened after ROUTE6, no device name may be present
        boolean included = isAndroidTunDevice(device);
        addRoutev6(network, included);
    }

    public void addRoutev6(String network, boolean included) {
        String[] v6parts = network.split("/");

        try {
            Inet6Address ip = (Inet6Address) InetAddress.getAllByName(v6parts[0])[0];
            int mask = Integer.parseInt(v6parts[1]);
            tunConfig.mRoutesv6.addIPv6(ip, mask, included);

        } catch (UnknownHostException e) {
            VpnStatus.logException(e);
        }


    }

    private boolean isAndroidTunDevice(String device) {
        return device != null &&
                (device.startsWith("tun") || "(null)".equals(device) || VPNSERVICE_TUN.equals(device));
    }

    public void setMtu(int mtu) {
        tunConfig.mMtu = mtu;
    }

    public void setLocalIP(CIDRIP cdrip) {
        tunConfig.mLocalIP = cdrip;
    }

    public void setLocalIP(String local, String netmask, int mtu, String mode) {
        tunConfig.mLocalIP = new CIDRIP(local, netmask);
        tunConfig.mMtu = mtu;
        mRemoteGW = null;

        long netMaskAsInt = CIDRIP.getInt(netmask);

        if (tunConfig.mLocalIP.len == 32 && !netmask.equals("255.255.255.255")) {
            // get the netmask as IP

            int masklen;
            long mask;
            if ("net30".equals(mode)) {
                masklen = 30;
                mask = 0xfffffffc;
            } else {
                masklen = 31;
                mask = 0xfffffffe;
            }

            // Netmask is Ip address +/-1, assume net30/p2p with small net
            if ((netMaskAsInt & mask) == (tunConfig.mLocalIP.getInt() & mask)) {
                tunConfig.mLocalIP.len = masklen;
            } else {
                tunConfig.mLocalIP.len = 32;
                if (!"p2p".equals(mode))
                    VpnStatus.logWarning(R.string.ip_not_cidr, local, netmask, mode);
            }
        }
        if (("p2p".equals(mode) && tunConfig.mLocalIP.len < 32) || ("net30".equals(mode) && tunConfig.mLocalIP.len < 30)) {
            VpnStatus.logWarning(R.string.ip_looks_like_subnet, local, netmask, mode);
        }


        /* Workaround for Lollipop and higher, it does not route traffic to the VPNs own network mask */
        if (tunConfig.mLocalIP.len <= 31) {
            CIDRIP interfaceRoute = new CIDRIP(tunConfig.mLocalIP.mIp, tunConfig.mLocalIP.len);
            interfaceRoute.normalise();
            addRoute(interfaceRoute, true);
        }


        // Configurations are sometimes really broken...
        mRemoteGW = netmask;
    }

    public void setLocalIPv6(String ipv6addr) {
        tunConfig.mLocalIPv6 = ipv6addr;
    }

    @Override
    public void updateState(String state, String logmessage, int resid, ConnectionStatus level, Intent intent) {
        // If the process is not running, ignore any state,
        // Notification should be invisible in this state

        doSendBroadcast(state, level);
        if (mProcessThread == null && !mNotificationAlwaysVisible)
            return;

        String channel = NOTIFICATION_CHANNEL_NEWSTATUS_ID;
        // Display byte count only after being connected

        {
            if (level == LEVEL_CONNECTED) {
                mDisplayBytecount = true;
                mConnecttime = System.currentTimeMillis();
                if (!runningOnAndroidTV())
                    channel = NOTIFICATION_CHANNEL_BG_ID;
            } else {
                mDisplayBytecount = false;
            }

            // Other notifications are shown,
            // This also mean we are no longer connected, ignore bytecount messages until next
            // CONNECTED
            // Does not work :(
            showNotification(VpnStatus.getLastCleanLogMessage(this),
                    VpnStatus.getLastCleanLogMessage(this), channel, 0, level, intent);

        }
    }

    @Override
    public void setConnectedVPN(String uuid) {
    }

    private void doSendBroadcast(String state, ConnectionStatus level) {
        Intent vpnstatus = new Intent();
        vpnstatus.setAction("de.blinkt.openvpn.VPN_STATUS");
        vpnstatus.putExtra("status", level.toString());
        vpnstatus.putExtra("detailstatus", state);
        sendBroadcast(vpnstatus, permission.ACCESS_NETWORK_STATE);
    }

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        if (mDisplayBytecount) {
            String netstat = String.format(getString(R.string.statusline_bytecount),
                    humanReadableByteCount(in, false, getResources()),
                    humanReadableByteCount(diffIn / OpenVPNManagement.mBytecountInterval, true, getResources()),
                    humanReadableByteCount(out, false, getResources()),
                    humanReadableByteCount(diffOut / OpenVPNManagement.mBytecountInterval, true, getResources()));


            showNotification(netstat, null, NOTIFICATION_CHANNEL_BG_ID, mConnecttime, LEVEL_CONNECTED, null);
        }

    }

    @Override
    public boolean handleMessage(Message msg) {
        Runnable r = msg.getCallback();
        if (r != null) {
            r.run();
            return true;
        } else {
            return false;
        }
    }

    public OpenVPNManagement getManagement() {
        return mManagement;
    }

    public String getTunReopenStatus() {
        String currentConfiguration = getTunConfigString(tunConfig);
        if (currentConfiguration.equals(getTunConfigString(mLastTunCfg))) {
            return "NOACTION";
        } else {
            return "OPEN_BEFORE_CLOSE";
        }
    }

    public void requestInputFromUser(int resid, String needed) {
        VpnStatus.updateStateString("NEED", "need " + needed, resid, LEVEL_WAITING_FOR_USER_INPUT);
        showNotification(getString(resid), getString(resid), NOTIFICATION_CHANNEL_NEWSTATUS_ID, 0, LEVEL_WAITING_FOR_USER_INPUT, null);
    }


    private Intent getWebAuthIntent(String url, boolean external, Notification.Builder nbuilder)
    {
        int reason = R.string.openurl_requested;
        nbuilder.setContentTitle(getString(reason));

        nbuilder.setContentText(url);
        Intent intent = VariantConfig.getOpenUrlIntent(this, external);
        intent.setData(Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public void trigger_sso(String info) {
    /*    String channel = NOTIFICATION_CHANNEL_USERREQ_ID;
        String method = info.split(":", 2)[0];

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Notification.Builder nbuilder = new Notification.Builder(this);
        nbuilder.setAutoCancel(true);
        int icon = android.R.drawable.ic_dialog_info;
        nbuilder.setSmallIcon(icon);

        Intent intent;
        int reason;

        switch (method) {
            case "OPEN_URL": {
                reason = R.string.openurl_requested;
                String url = info.split(":", 2)[1];
                intent = getWebAuthIntent(url, false, nbuilder);

                break;
            }
            case "WEB_AUTH": {
                reason = R.string.openurl_requested;
                String[] parts = info.split(":", 3);
                if (parts.length < 3) {
                    VpnStatus.logError("WEB_AUTH method with invalid argument found");
                    return;
                }
                String url = parts[2];
                String[] flags = parts[1].split(",");
                boolean external = false;
                for (String flag : flags) {
                    if (flag.equals("external")) {
                        external = true;
                        break;
                    }
                }

                intent = getWebAuthIntent(url, external, nbuilder);
                break;
            }
            case "CR_TEXT":
                String challenge = info.split(":", 2)[1];
                reason = R.string.crtext_requested;
                nbuilder.setContentTitle(getString(reason));
                nbuilder.setContentText(challenge);

                intent = new Intent();
                intent.setComponent(new ComponentName(this, getPackageName() + ".activities.CredentialsPopup"));

                intent.putExtra(EXTRA_CHALLENGE_TXT, challenge);

                break;
            default:
                VpnStatus.logError("Unknown SSO method found: " + method);
                return;
        }

        // updateStateString trigger the notification of the VPN to be refreshed, save this intent
        // to have that notification also this intent to be set
        PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        VpnStatus.updateStateString("USER_INPUT", "waiting for user input", reason, LEVEL_WAITING_FOR_USER_INPUT, intent);

        nbuilder.setContentIntent(pIntent);

        jbNotificationExtras(PRIORITY_MAX, nbuilder);
        lpNotificationExtras(nbuilder, Notification.CATEGORY_STATUS);

        String channel = NOTIFICATION_CHANNEL_USERREQ_ID;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //noinspection NewApi
            nbuilder.setChannelId(channel);
        }

        @SuppressWarnings("deprecation")
        Notification notification = nbuilder.getNotification();


        int notificationId = channel.hashCode();

        mNotificationManager.notify(notificationId, notification);
        */
    }
}
