/*
 * Copyright (C) 2012-2013 Tobias Brunner
 * Copyright (C) 2012 Giuliano Grassi
 * Copyright (C) 2012 Ralf Sager
 * Hochschule fuer Technik Rapperswil
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

package org.strongswan.android.logic;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.system.OsConstants;

import com.protonvpn.android.api.ProtonApiRetroFit;
import com.protonvpn.android.appconfig.AppConfig;
import com.protonvpn.android.bus.EventBus;
import com.protonvpn.android.components.NotificationHelper;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.ConnectionParams;
import com.protonvpn.android.models.vpn.ConnectionParamsIKEv2;
import com.protonvpn.android.utils.Constants;
import com.protonvpn.android.utils.Log;
import com.protonvpn.android.utils.ProtonLogger;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.utils.Storage;
import com.protonvpn.android.vpn.VpnStateMonitor;

import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.data.VpnType.VpnTypeFeature;
import org.strongswan.android.logic.VpnStateService.ErrorState;
import org.strongswan.android.logic.VpnStateService.State;
import org.strongswan.android.logic.imc.ImcState;
import org.strongswan.android.logic.imc.RemediationInstruction;
import org.strongswan.android.utils.IPRange;
import org.strongswan.android.utils.IPRangeSet;
import org.strongswan.android.utils.SettingsWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;

import javax.inject.Inject;

import androidx.work.WorkManager;
import dagger.android.AndroidInjection;

public class CharonVpnService extends VpnService implements Runnable {

    private static final String TAG = CharonVpnService.class.getSimpleName();
    public static final String LOG_FILE = "charon.log";
    public static final String DISCONNECT_ACTION = "org.strongswan.android.CharonVpnService.DISCONNECT";
    public static final int VPN_STATE_NOTIFICATION_ID = 1;
    private static final String VPN_SERVICE_ACTION = "android.net.VpnService";

    private String mAppDir;
    private String mLogFile;
    private Thread mConnectionHandler;
    private Thread logCaptureThread = null;
    private VpnProfile mCurrentProfile;
    private volatile String mCurrentCertificateAlias;
    private volatile String mCurrentUserCertificateAlias;
    private VpnProfile mNextProfile;
    private volatile boolean mProfileUpdated;
    private volatile boolean mTerminate;
    private volatile boolean mIsDisconnecting;
    private volatile boolean mShowNotification;
    private VpnStateService mService;
    private DisconnectListener receiver;
    private BuilderAdapter mBuilderAdapter = new BuilderAdapter();
    private final Object mServiceLock = new Object();
    public static final String KEY_IS_RETRY = "retry";
    @Inject ProtonApiRetroFit api;
    @Inject UserData userData;
    @Inject AppConfig appConfig;
    @Inject ServerManager manager;
    @Inject VpnStateMonitor stateMonitor;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            /* since the service is local this is
            theoretically only called when the process is terminated */
            synchronized (mServiceLock) {
                mService = null;
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (mServiceLock) {
                mService = ((VpnStateService.LocalBinder) service).getService();
            }
            /* we are now ready to start the handler thread */
            mConnectionHandler.start();
        }
    };

    /**
     * as defined in charonservice.h
     */
    static final int STATE_CHILD_SA_UP = 1;
    static final int STATE_CHILD_SA_DOWN = 2;
    static final int STATE_AUTH_ERROR = 3;
    static final int STATE_PEER_AUTH_ERROR = 4;
    static final int STATE_LOOKUP_ERROR = 5;
    static final int STATE_UNREACHABLE_ERROR = 6;
    static final int STATE_GENERIC_ERROR = 7;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            handleRestoreState();
        }
        else if (VPN_SERVICE_ACTION.equals(intent.getAction()) && userData.isLoggedIn()) {
            handleAlwaysOn();
        }
        else {
            startForeground(Constants.NOTIFICATION_ID, stateMonitor.buildNotification());
            ConnectionParamsIKEv2 serverToConnect = Storage.load(
                ConnectionParams.class, ConnectionParamsIKEv2.class);
            setNextProfile(serverToConnect != null ?
                serverToConnect.getStrongSwanProfile(userData, appConfig) : null);
            Log.e("start next profile: " + (serverToConnect == null));
            return serverToConnect != null ? START_STICKY : START_NOT_STICKY;
        }
        Log.e("start not sticky");
        return START_NOT_STICKY;
    }

    private void handleRestoreState() {
        ConnectionParamsIKEv2 lastServer = Storage.load(
            ConnectionParams.class, ConnectionParamsIKEv2.class);
        if (lastServer == null) {
            stopSelf();
        }
        else {
            lastServer.getProfile().getWrapper().setDeliverer(manager);
            if (!stateMonitor.onRestoreProcess(this, lastServer.getProfile())) {
                stopSelf();
            }
        }
    }

    private void handleAlwaysOn() {
        Profile profile = manager.getDefaultConnection();
        if (profile == null) {
            stopSelf();
        }
        else {
            Log.e("handle always on");
            stateMonitor.connect(this, profile);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AndroidInjection.inject(this);
        NotificationHelper.INSTANCE.initNotificationChannel(getApplicationContext());
        startForeground(Constants.NOTIFICATION_ID, stateMonitor.buildNotification());
        mLogFile = getFilesDir().getAbsolutePath() + File.separator + LOG_FILE;
        mAppDir = getFilesDir().getAbsolutePath();
        /* use a separate thread as main thread for charon */
        mConnectionHandler = new Thread(this);
        /* the thread is started when the service is bound */
        bindService(new Intent(this, VpnStateService.class), mServiceConnection, Service.BIND_AUTO_CREATE);
        EventBus.getInstance().register(this);
        receiver = new DisconnectListener();
        this.registerReceiver(receiver, new IntentFilter("Disconnect"));
        startCaptureLogFile();
    }

    public void startCaptureLogFile() {
        if (logCaptureThread != null) {
            return;
        }

        logCaptureThread = new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("logcat -s charon -T 1 -v raw");

                BufferedReader bufferedReader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line = bufferedReader.readLine();
                while (!mTerminate && line != null) {
                    ProtonLogger.INSTANCE.log(line);
                    line = bufferedReader.readLine();
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        });
        logCaptureThread.start();
    }

    @Override
    public void onRevoke() {    /* the system revoked the rights grated with the initial prepare() call.
     * called when the user clicks disconnect in the system's VPN dialog */
        setNextProfile(null);
    }

    @Override
    public void onDestroy() {
        mTerminate = true;
        logCaptureThread = null;
        stopCurrentConnection();
        setNextProfile(null);
        this.unregisterReceiver(receiver);
        EventBus.getInstance().unregister(this);
        try {
            mConnectionHandler.join();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        unbindService(mServiceConnection);
        Log.e("on destroy");
        stopForeground(true);
        stopSelf();
        super.onDestroy();
    }

    /**
     * Set the profile that is to be initiated next. Notify the handler thread.
     *
     * @param profile the profile to initiate
     */
    private void setNextProfile(VpnProfile profile) {
        synchronized (this) {
            this.mNextProfile = profile;
            mProfileUpdated = true;
            notifyAll();
        }
    }

    @Override
    public void run() {
        while (true) {
            synchronized (this) {
                try {
                    while (!mProfileUpdated) {
                        wait();
                    }

                    mProfileUpdated = false;
                    stopCurrentConnection();
                    if (mNextProfile == null) {
                        setState(State.DISABLED);
                        if (mTerminate) {
                            break;
                        }
                    }
                    else {
                        initNativeConnection();
                    }
                }
                catch (InterruptedException ex) {
                    stopCurrentConnection();
                    setState(State.DISABLED);
                }
            }
        }
    }

    private void initNativeConnection() {
        mCurrentProfile = mNextProfile;
        mNextProfile = null;
        mCurrentCertificateAlias = mCurrentProfile.getCertificateAlias();
        mCurrentUserCertificateAlias = mCurrentProfile.getUserCertificateAlias();

        mIsDisconnecting = false;
        startConnection(mCurrentProfile);

        mBuilderAdapter.setProfile(mCurrentProfile);
        if (initializeCharon(mBuilderAdapter, mLogFile, mAppDir,
            mCurrentProfile.getVpnType().has(VpnTypeFeature.BYOD))) {
            android.util.Log.i(TAG, "charon started");
            SettingsWriter writer = new SettingsWriter();
            writer.setValue("global.language", Locale.getDefault().getLanguage());
            writer.setValue("global.mtu", mCurrentProfile.getMTU());
            writer.setValue("global.nat_keepalive", mCurrentProfile.getNATKeepAlive());
            writer.setValue("global.rsa_pss", (mCurrentProfile.getFlags() & VpnProfile.FLAGS_RSA_PSS) != 0);
            writer.setValue("global.crl", (mCurrentProfile.getFlags() & VpnProfile.FLAGS_DISABLE_CRL) == 0);
            writer.setValue("global.ocsp", (mCurrentProfile.getFlags() & VpnProfile.FLAGS_DISABLE_OCSP) == 0);
            writer.setValue("connection.strict_revocation",
                (mCurrentProfile.getFlags() & VpnProfile.FLAGS_STRICT_REVOCATION) != 0);
            writer.setValue("connection.type", mCurrentProfile.getVpnType().getIdentifier());
            writer.setValue("connection.server", mCurrentProfile.getGateway());
            writer.setValue("connection.port", mCurrentProfile.getPort());
            writer.setValue("connection.username", mCurrentProfile.getUserName());
            writer.setValue("connection.password", mCurrentProfile.getUserPassword());
            writer.setValue("connection.local_id", mCurrentProfile.getLocalId());
            writer.setValue("connection.remote_id", mCurrentProfile.getRemoteId());
            writer.setValue("connection.certreq",
                (mCurrentProfile.getFlags() & VpnProfile.FLAGS_SUPPRESS_CERT_REQS) == 0);
            writer.setValue("connection.ike_proposal", mCurrentProfile.getIkeProposal());
            writer.setValue("connection.esp_proposal", mCurrentProfile.getEspProposal());
            initiate(writer.serialize());
        }
        else {
            android.util.Log.e(TAG, "failed to start charon");
            setError(ErrorState.GENERIC_ERROR);
            setState(State.DISABLED);
            mCurrentProfile = null;
        }
    }

    /**
     * Stop any existing connection by deinitializing charon.
     */
    private void stopCurrentConnection() {
        synchronized (this) {
            if (mNextProfile != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mBuilderAdapter.setProfile(mNextProfile);
            }
            if (mCurrentProfile != null) {
                setState(State.DISCONNECTING);
                WorkManager.getInstance(this).cancelAllWork();
                mIsDisconnecting = true;
                deinitializeCharon();
                mCurrentProfile = null;
                removeNotification();
            }
        }
    }

    /**
     * Remove the permanent notification.
     */
    private void removeNotification() {
        mShowNotification = false;
        Log.e("remove notification");
        stopForeground(true);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(VPN_STATE_NOTIFICATION_ID);
    }

    public class DisconnectListener extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            mService.disconnect();
        }
    }

    /**
     * Notify the state service about a new connection attempt.
     * Called by the handler thread.
     *
     * @param profile currently active VPN profile
     */
    private void startConnection(VpnProfile profile) {
        synchronized (mServiceLock) {
            if (mService != null) {
                mService.startConnection(profile);
            }
        }
    }

    /**
     * Update the current VPN state on the state service. Called by the handler
     * thread and any of charon's threads.
     *
     * @param state current state
     */
    private void setState(State state) {
        synchronized (mServiceLock) {
            if (mService != null) {
                mService.setState(state);
            }
        }
    }

    /**
     * Set an error on the state service. Called by the handler thread and any
     * of charon's threads.
     *
     * @param error error state
     */
    private void setError(ErrorState error) {
        synchronized (mServiceLock) {
            if (mService != null) {
                mService.setError(error);
            }
        }
    }

    /**
     * Set the IMC state on the state service. Called by the handler thread and
     * any of charon's threads.
     *
     * @param state IMC state
     */
    private void setImcState(ImcState state) {
        synchronized (mServiceLock) {
            if (mService != null) {
                mService.setImcState(state);
            }
        }
    }

    /**
     * Set an error on the state service. Called by the handler thread and any
     * of charon's threads.
     *
     * @param error error state
     */
    private void setErrorDisconnect(ErrorState error) {
        synchronized (mServiceLock) {
            if (mService != null) {
                if (!mIsDisconnecting) {
                    mService.setError(error);
                }
            }
        }
    }

    /**
     * Updates the state of the current connection.
     * Called via JNI by different threads (but not concurrently).
     *
     * @param status new state
     */
    public void updateStatus(int status) {
        switch (status) {
            case STATE_CHILD_SA_DOWN:
                if (!mIsDisconnecting) {
                    setState(State.CONNECTING);
                }
                break;
            case STATE_CHILD_SA_UP:
                setState(State.CONNECTED);
                break;
            case STATE_AUTH_ERROR:
                setErrorDisconnect(ErrorState.AUTH_FAILED);
                break;
            case STATE_PEER_AUTH_ERROR:
                setErrorDisconnect(ErrorState.PEER_AUTH_FAILED);
                break;
            case STATE_LOOKUP_ERROR:
                setErrorDisconnect(ErrorState.LOOKUP_FAILED);
                break;
            case STATE_UNREACHABLE_ERROR:
                setErrorDisconnect(ErrorState.UNREACHABLE);
                break;
            case STATE_GENERIC_ERROR:
                setErrorDisconnect(ErrorState.GENERIC_ERROR);
                break;
            default:
                Log.e("Unknown status code received");
                break;
        }
    }

    /**
     * Updates the IMC state of the current connection.
     * Called via JNI by different threads (but not concurrently).
     *
     * @param value new state
     */
    public void updateImcState(int value) {
        ImcState state = ImcState.fromValue(value);
        if (state != null) {
            setImcState(state);
        }
    }

    /**
     * Add a remediation instruction to the VPN state service.
     * Called via JNI by different threads (but not concurrently).
     *
     * @param xml XML text
     */
    public void addRemediationInstruction(String xml) {
        for (RemediationInstruction instruction : RemediationInstruction.fromXml(xml)) {
            synchronized (mServiceLock) {
                if (mService != null) {
                    mService.addRemediationInstruction(instruction);
                }
            }
        }
    }

    /**
     * Function called via JNI to generate a list of DER encoded CA certificates
     * as byte array.
     *
     * @return a list of DER encoded CA certificates
     */
    private byte[][] getTrustedCertificates() {
        ArrayList<byte[]> certs = new ArrayList<byte[]>();
        TrustedCertificateManager certman = TrustedCertificateManager.getInstance().load();
        try {
            String alias = this.mCurrentCertificateAlias;
            if (alias != null) {
                X509Certificate cert = certman.getCACertificateFromAlias(alias);
                if (cert == null) {
                    return null;
                }
                certs.add(cert.getEncoded());
            }
            else {
                for (X509Certificate cert : certman.getAllCACertificates().values()) {
                    certs.add(cert.getEncoded());
                }
            }
        }
        catch (CertificateEncodingException e) {
            e.printStackTrace();
            return null;
        }
        return certs.toArray(new byte[certs.size()][]);
    }

    /**
     * Function called via JNI to get a list containing the DER encoded certificates
     * of the user selected certificate chain (beginning with the user certificate).
     * <p>
     * Since this method is called from a thread of charon's thread pool we are safe
     * to call methods on KeyChain directly.
     *
     * @return list containing the certificates (first element is the user certificate)
     * @throws InterruptedException
     * @throws KeyChainException
     * @throws CertificateEncodingException
     */
    private byte[][] getUserCertificate() throws KeyChainException, InterruptedException,
        CertificateEncodingException {
        ArrayList<byte[]> encodings = new ArrayList<byte[]>();
        X509Certificate[] chain =
            KeyChain.getCertificateChain(getApplicationContext(), mCurrentUserCertificateAlias);
        if (chain == null || chain.length == 0) {
            return null;
        }
        for (X509Certificate cert : chain) {
            encodings.add(cert.getEncoded());
        }
        return encodings.toArray(new byte[encodings.size()][]);
    }

    /**
     * Function called via JNI to get the private key the user selected.
     * <p>
     * Since this method is called from a thread of charon's thread pool we are safe
     * to call methods on KeyChain directly.
     *
     * @return the private key
     * @throws InterruptedException
     * @throws KeyChainException
     * @throws CertificateEncodingException
     */
    private PrivateKey getUserKey() throws KeyChainException, InterruptedException {
        return KeyChain.getPrivateKey(getApplicationContext(), mCurrentUserCertificateAlias);
    }

    /**
     * Initialization of charon, provided by libandroidbridge.so
     *
     * @param builder BuilderAdapter for this connection
     * @param logfile absolute path to the logfile
     * @param appdir  absolute path to the data directory of the app
     * @param byod    enable BYOD features
     * @return TRUE if initialization was successful
     */
    public native boolean initializeCharon(BuilderAdapter builder, String logfile, String appdir,
                                           boolean byod);

    /**
     * Deinitialize charon, provided by libandroidbridge.so
     */
    public native void deinitializeCharon();

    /**
     * Initiate VPN, provided by libandroidbridge.so
     */
    public native void initiate(String config);

    /**
     * Adapter for VpnService.Builder which is used to access it safely via JNI.
     * There is a corresponding C object to access it from native code.
     */
    public class BuilderAdapter {

        private VpnProfile mProfile;
        private VpnService.Builder mBuilder;
        private BuilderCache mCache;
        private BuilderCache mEstablishedCache;
        private PacketDropper mDropper = new PacketDropper();

        public synchronized void setProfile(VpnProfile profile) {
            mProfile = profile;
            mBuilder = createBuilder(mProfile.getName());
            mCache = new BuilderCache(mProfile);
        }

        private VpnService.Builder createBuilder(String name) {
            VpnService.Builder builder = new CharonVpnService.Builder();
            builder.setSession(name);

            /* even though the option displayed in the system dialog says "Configure"
             * we just use our main Activity */
            Context context = getApplicationContext();
            Intent intent = new Intent(context, Constants.INSTANCE.getMAIN_ACTIVITY_CLASS());
            PendingIntent pending =
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setConfigureIntent(pending);
            return builder;
        }

        public synchronized boolean addAddress(String address, int prefixLength) {
            try {
                mCache.addAddress(address, prefixLength);
            }
            catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean addDnsServer(String address) {
            try {
                mBuilder.addDnsServer(address);
                mCache.recordAddressFamily(address);
            }
            catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean addRoute(String address, int prefixLength) {
            try {
                mCache.addRoute(address, prefixLength);
            }
            catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean addSearchDomain(String domain) {
            try {
                mBuilder.addSearchDomain(domain);
            }
            catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean setMtu(int mtu) {
            try {
                mCache.setMtu(mtu);
            }
            catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        private synchronized ParcelFileDescriptor establishIntern() {
            ParcelFileDescriptor fd;
            try {
                mCache.applyData(mBuilder);
                fd = mBuilder.establish();
                if (fd != null) {
                    closeBlocking();
                }
            }
            catch (Exception ex) {
                if (ex instanceof SecurityException && ex.getMessage().contains("INTERACT_ACROSS_USERS")) {
                    setErrorDisconnect(ErrorState.MULTI_USER_PERMISSION);
                }
                ex.printStackTrace();
                return null;
            }
            if (fd == null) {
                return null;
            }
            /* now that the TUN device is created we don't need the current
             * builder anymore, but we might need another when reestablishing */
            mBuilder = createBuilder(mProfile.getName());
            mEstablishedCache = mCache;
            mCache = new BuilderCache(mProfile);
            return fd;
        }

        public synchronized int establish() {
            ParcelFileDescriptor fd = establishIntern();
            return fd != null ? fd.detachFd() : -1;
        }

        // Deprecated in favor of using native always-on block connections
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Deprecated
        public synchronized void establishBlocking() {
            /* just choose some arbitrary values to block all traffic (except for what's configured in the
            profile) */
            mCache.addAddress("172.16.252.1", 32);
            mCache.addAddress("fd00::fd02:1", 128);
            mCache.addRoute("0.0.0.0", 0);
            mCache.addRoute("::", 0);
            /* set DNS servers to avoid DNS leak later */
            mBuilder.addDnsServer("8.8.8.8");
            mBuilder.addDnsServer("2001:4860:4860::8888");
            /* use blocking mode to simplify packet dropping */
            mBuilder.setBlocking(true);

            ParcelFileDescriptor fd = establishIntern();
            if (fd != null) {
                mDropper.start(fd);
            }
        }

        public synchronized void closeBlocking() {
            mDropper.stop();
        }

        public synchronized int establishNoDns() {
            ParcelFileDescriptor fd;

            if (mEstablishedCache == null) {
                return -1;
            }
            try {
                Builder builder = createBuilder(mProfile.getName());
                mEstablishedCache.applyData(builder);
                fd = builder.establish();
            }
            catch (Exception ex) {
                ex.printStackTrace();
                return -1;
            }
            if (fd == null) {
                return -1;
            }
            return fd.detachFd();
        }

        private class PacketDropper implements Runnable {

            private ParcelFileDescriptor mFd;
            private Thread mThread;

            public void start(ParcelFileDescriptor fd) {
                mFd = fd;
                mThread = new Thread(this);
                mThread.start();
            }

            public void stop() {
                if (mFd != null) {
                    try {
                        mThread.interrupt();
                        mThread.join();
                        mFd.close();
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                    mFd = null;
                }
            }

            @Override
            public synchronized void run() {
                try {
                    FileInputStream plain = new FileInputStream(mFd.getFileDescriptor());
                    ByteBuffer packet = ByteBuffer.allocate(mCache.mMtu);
                    while (true) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {    /* just read and ignore
                        all data, regular read()
                             is not interruptible */
                            int len = plain.getChannel().read(packet);
                            packet.clear();
                            if (len < 0) {
                                break;
                            }
                        }
                        else {    /* this is rather ugly but on older platforms not even the NIO version of
                         read() is interruptible */
                            boolean wait = true;
                            if (plain.available() > 0) {
                                int len = plain.read(packet.array());
                                packet.clear();
                                if (len < 0 || Thread.interrupted()) {
                                    break;
                                }
                                /* check again right away, there may be another packet */
                                wait = false;
                            }
                            if (wait) {
                                Thread.sleep(250);
                            }
                        }
                    }
                }
                catch (ClosedByInterruptException | InterruptedException e) {
                    /* regular interruption */
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Cache non DNS related information so we can recreate the builder without
     * that information when reestablishing IKE_SAs
     */
    public class BuilderCache {

        private final List<IPRange> mAddresses = new ArrayList<>();
        private final List<IPRange> mRoutesIPv4 = new ArrayList<>();
        private final List<IPRange> mRoutesIPv6 = new ArrayList<>();
        private final IPRangeSet mIncludedSubnetsv4 = new IPRangeSet();
        private final IPRangeSet mIncludedSubnetsv6 = new IPRangeSet();
        private final IPRangeSet mExcludedSubnets;
        private final int mSplitTunneling;
        private final VpnProfile.SelectedAppsHandling mAppHandling;
        private final SortedSet<String> mSelectedApps;
        private int mMtu;
        private boolean mIPv4Seen, mIPv6Seen;

        public BuilderCache(VpnProfile profile) {
            IPRangeSet included = IPRangeSet.fromString(profile.getIncludedSubnets());
            for (IPRange range : included) {
                if (range.getFrom() instanceof Inet4Address) {
                    mIncludedSubnetsv4.add(range);
                }
                else if (range.getFrom() instanceof Inet6Address) {
                    mIncludedSubnetsv6.add(range);
                }
            }

            mExcludedSubnets = IPRangeSet.fromString(profile.getExcludedSubnets());
            Integer splitTunneling = profile.getSplitTunneling();
            mSplitTunneling = splitTunneling != null ? splitTunneling : 0;

            VpnProfile.SelectedAppsHandling appHandling = profile.getSelectedAppsHandling(userData);
            mSelectedApps = profile.getSelectedAppsSet();
            switch (appHandling) {
                case SELECTED_APPS_DISABLE:
                    appHandling = VpnProfile.SelectedAppsHandling.SELECTED_APPS_EXCLUDE;
                    mSelectedApps.clear();
                case SELECTED_APPS_EXCLUDE:
                    //      mSelectedApps.add(getPackageName());
                    break;
                case SELECTED_APPS_ONLY:
                    //     mSelectedApps.remove(getPackageName());
                    break;
            }
            mAppHandling = appHandling;
        }

        public void addAddress(String address, int prefixLength) {
            try {
                mAddresses.add(new IPRange(address, prefixLength));
                recordAddressFamily(address);
            }
            catch (UnknownHostException ex) {
                ex.printStackTrace();
            }
        }

        public void addRoute(String address, int prefixLength) {
            try {
                if (isIPv6(address)) {
                    mRoutesIPv6.add(new IPRange(address, prefixLength));
                }
                else {
                    mRoutesIPv4.add(new IPRange(address, prefixLength));
                }
            }
            catch (UnknownHostException ex) {
                ex.printStackTrace();
            }
        }

        public void setMtu(int mtu) {
            mMtu = mtu;
        }

        public void recordAddressFamily(String address) {
            try {
                if (isIPv6(address)) {
                    mIPv6Seen = true;
                }
                else {
                    mIPv4Seen = true;
                }
            }
            catch (UnknownHostException ex) {
                ex.printStackTrace();
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void applyData(VpnService.Builder builder) {
            for (IPRange address : mAddresses) {
                builder.addAddress(address.getFrom(), address.getPrefix());
            }
            /* add routes depending on whether split tunneling is allowed or not,
             * that is, whether we have to handle and block non-VPN traffic */
            if ((mSplitTunneling & VpnProfile.SPLIT_TUNNELING_BLOCK_IPV4) == 0) {
                if (mIPv4Seen) {    /* split tunneling is used depending on the routes and configuration */
                    IPRangeSet ranges = new IPRangeSet();
                    if (mIncludedSubnetsv4.size() > 0) {
                        ranges.add(mIncludedSubnetsv4);
                    }
                    else {
                        ranges.addAll(mRoutesIPv4);
                    }
                    ranges.remove(mExcludedSubnets);
                    for (IPRange subnet : ranges.subnets()) {
                        try {
                            builder.addRoute(subnet.getFrom(), subnet.getPrefix());
                        }
                        catch (IllegalArgumentException e) {
                            /* some Android versions don't seem to like
                               multicast addresses here, * ignore it for now */
                            if (!subnet.getFrom().isMulticastAddress()) {
                                throw e;
                            }
                        }
                    }
                }
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    /* allow traffic that would otherwise be blocked to bypass the VPN */
                    builder.allowFamily(OsConstants.AF_INET);
                }
            }
            else if (mIPv4Seen) { /* only needed if we've seen any addresses.  otherwise, traffic is
            blocked by default (we also install no routes in that case) */
                builder.addRoute("0.0.0.0", 0);
            }
            /* same thing for IPv6 */
            if ((mSplitTunneling & VpnProfile.SPLIT_TUNNELING_BLOCK_IPV6) == 0) {
                if (mIPv6Seen) {
                    IPRangeSet ranges = new IPRangeSet();
                    if (mIncludedSubnetsv6.size() > 0) {
                        ranges.add(mIncludedSubnetsv6);
                    }
                    else {
                        ranges.addAll(mRoutesIPv6);
                    }
                    ranges.remove(mExcludedSubnets);
                    for (IPRange subnet : ranges.subnets()) {
                        try {
                            builder.addRoute(subnet.getFrom(), subnet.getPrefix());
                        }
                        catch (IllegalArgumentException e) {
                            if (!subnet.getFrom().isMulticastAddress()) {
                                throw e;
                            }
                        }
                    }
                }
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    builder.allowFamily(OsConstants.AF_INET6);
                }
            }
            else if (mIPv6Seen) {
                builder.addRoute("::", 0);
            }
            /* apply selected applications */
            if (mSelectedApps.size() > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                switch (mAppHandling) {
                    case SELECTED_APPS_EXCLUDE:
                        for (String app : mSelectedApps) {
                            try {
                                builder.addDisallowedApplication(app);
                            }
                            catch (PackageManager.NameNotFoundException e) {
                                // possible if not configured via GUI or app was uninstalled
                            }
                        }
                        break;
                    case SELECTED_APPS_ONLY:
                        for (String app : mSelectedApps) {
                            try {
                                builder.addAllowedApplication(app);
                            }
                            catch (PackageManager.NameNotFoundException e) {
                                // possible if not configured via GUI or app was uninstalled
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
            if (mMtu == 0) {
                setMtu(userData.getMtuSize());
            }
            builder.setMtu(mMtu);
        }

        private boolean isIPv6(String address) throws UnknownHostException {
            InetAddress addr = InetAddress.getByName(address);
            if (addr instanceof Inet4Address) {
                return false;
            }
            else {
                return addr instanceof Inet6Address;
            }
        }
    }

    /**
     * Function called via JNI to determine information about the Android version.
     */
    private static String getAndroidVersion() {
        String version = "Android " + Build.VERSION.RELEASE + " - " + Build.DISPLAY;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            version += "/" + Build.VERSION.SECURITY_PATCH;
        }
        return version;
    }

    /**
     * Function called via JNI to determine information about the device.
     */
    private static String getDeviceString() {
        return Build.MODEL + " - " + Build.BRAND + "/" + Build.PRODUCT + "/" + Build.MANUFACTURER;
    }

}
