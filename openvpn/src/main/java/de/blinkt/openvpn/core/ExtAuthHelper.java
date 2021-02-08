/*
 * Copyright (c) 2012-2018 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.*;
import android.security.KeyChainException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import de.blinkt.openvpn.api.ExternalCertificateProvider;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.UnsupportedEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ExtAuthHelper {

    public static final String ACTION_CERT_CONFIGURATION = "de.blinkt.openvpn.api.ExternalCertificateConfiguration";
    public static final String ACTION_CERT_PROVIDER = "de.blinkt.openvpn.api.ExternalCertificateProvider";

    public static final String EXTRA_ALIAS = "de.blinkt.openvpn.api.KEY_ALIAS";
    public static final String EXTRA_DESCRIPTION = "de.blinkt.openvpn.api.KEY_DESCRIPTION";


    public static void setExternalAuthProviderSpinnerList(Spinner spinner, String selectedApp) {
        Context c = spinner.getContext();
        final PackageManager pm = c.getPackageManager();
        ArrayList<ExternalAuthProvider> extProviders = getExternalAuthProviderList(c);

        int selectedPos = -1;

        if (extProviders.size() ==0)
        {
            selectedApp = "";
            ExternalAuthProvider noauthprovider = new ExternalAuthProvider();
            noauthprovider.label = "No external auth provider found";
            noauthprovider.packageName = selectedApp;
            noauthprovider.configurable = false;
            extProviders.add(noauthprovider);
        }


        for (int i = 0; i < extProviders.size(); i++) {
            if (extProviders.get(i).packageName.equals(selectedApp))
                selectedPos = i;
        }
        SpinnerAdapter extAppAdapter = new ArrayAdapter<ExternalAuthProvider>(c, android.R.layout.simple_spinner_item, android.R.id.text1, extProviders);
        spinner.setAdapter(extAppAdapter);
        if (selectedPos != -1)
            spinner.setSelection(selectedPos);
    }

    static ArrayList<ExternalAuthProvider> getExternalAuthProviderList(Context c) {
        Intent configureExtAuth = new Intent(ACTION_CERT_CONFIGURATION);

        final PackageManager packageManager = c.getPackageManager();
        List<ResolveInfo> configureList =
                packageManager.queryIntentActivities(configureExtAuth, 0);

        Intent serviceExtAuth = new Intent(ACTION_CERT_PROVIDER);

        List<ResolveInfo> serviceList =
                packageManager.queryIntentServices(serviceExtAuth, 0);


        // For now only list those who appear in both lists

        ArrayList<ExternalAuthProvider> providers = new ArrayList<ExternalAuthProvider>();

        for (ResolveInfo service : serviceList) {
            ExternalAuthProvider ext = new ExternalAuthProvider();
            ext.packageName = service.serviceInfo.packageName;

            ext.label = (String) service.serviceInfo.applicationInfo.loadLabel(packageManager);

            for (ResolveInfo activity : configureList) {
                if (service.serviceInfo.packageName.equals(activity.activityInfo.packageName)) {
                    ext.configurable = true;
                }
            }
            providers.add(ext);

        }
        return providers;

    }

    @Nullable
    @WorkerThread
    public static byte[] signData(@NonNull Context context,
                                  @NonNull String extAuthPackageName,
                                  @NonNull String alias,
                                  @NonNull byte[] data,
                                  @NonNull Bundle extra
    ) throws KeyChainException, InterruptedException

    {


        try (ExternalAuthProviderConnection authProviderConnection =
                     bindToExtAuthProvider(context.getApplicationContext(), extAuthPackageName)) {
            ExternalCertificateProvider externalAuthProvider = authProviderConnection.getService();

            byte[] result = externalAuthProvider.getSignedDataWithExtra(alias, data, extra);
            // When the desired method is not implemented, a default implementation is called, returning null
            if (result == null)
                result = externalAuthProvider.getSignedData(alias, data);

            return result;

        } catch (RemoteException e) {
            throw new KeyChainException(e);
        }
    }

    @Nullable
    @WorkerThread
    public static X509Certificate[] getCertificateChain(@NonNull Context context,
                                                        @NonNull String extAuthPackageName,
                                                        @NonNull String alias) throws KeyChainException {

        final byte[] certificateBytes;
        try (ExternalAuthProviderConnection authProviderConnection = bindToExtAuthProvider(context.getApplicationContext(), extAuthPackageName)) {
            ExternalCertificateProvider externalAuthProvider = authProviderConnection.getService();
            certificateBytes = externalAuthProvider.getCertificateChain(alias);
            if (certificateBytes == null) {
                return null;
            }
            Collection<X509Certificate> chain = toCertificates(certificateBytes);
            return chain.toArray(new X509Certificate[chain.size()]);

        } catch (RemoteException | RuntimeException | InterruptedException e) {
            throw new KeyChainException(e);
        }
    }

    public static Bundle getCertificateMetaData(@NonNull Context context,
                                                @NonNull String extAuthPackageName,
                                                String alias) throws KeyChainException
    {
        try (ExternalAuthProviderConnection authProviderConnection = bindToExtAuthProvider(context.getApplicationContext(), extAuthPackageName)) {
            ExternalCertificateProvider externalAuthProvider = authProviderConnection.getService();
            return externalAuthProvider.getCertificateMetaData(alias);

        } catch (RemoteException | RuntimeException | InterruptedException e) {
            throw new KeyChainException(e);
        }
    }

    public static Collection<X509Certificate> toCertificates(@NonNull byte[] bytes) {
        final String BEGINCERT = "-----BEGIN CERTIFICATE-----";
        try {
            Vector<X509Certificate> retCerts = new Vector<>();
            // Java library is broken, although the javadoc says it will extract all certificates from a byte array
            // it only extracts the first one
            String allcerts = new String(bytes, "iso8859-1");
            String[] certstrings = allcerts.split(BEGINCERT);
            for (String certstring: certstrings) {
                certstring = BEGINCERT + certstring;
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                retCerts.addAll((Collection<? extends X509Certificate>) certFactory.generateCertificates(
                        new ByteArrayInputStream((certstring.getBytes("iso8859-1")))));

            }
            return retCerts;

        } catch (CertificateException e) {
            throw new AssertionError(e);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    // adapted form Keychain
    @WorkerThread
    public static ExternalAuthProviderConnection bindToExtAuthProvider(@NonNull Context context, String packagename) throws KeyChainException, InterruptedException {
        ensureNotOnMainThread(context);
        final BlockingQueue<ExternalCertificateProvider> q = new LinkedBlockingQueue<>(1);
        ServiceConnection extAuthServiceConnection = new ServiceConnection() {
            volatile boolean mConnectedAtLeastOnce = false;

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (!mConnectedAtLeastOnce) {
                    mConnectedAtLeastOnce = true;
                    try {
                        q.put(ExternalCertificateProvider.Stub.asInterface(service));
                    } catch (InterruptedException e) {
                        // will never happen, since the queue starts with one available slot
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };
        Intent intent = new Intent(ACTION_CERT_PROVIDER);
        intent.setPackage(packagename);

        if (!context.bindService(intent, extAuthServiceConnection, Context.BIND_AUTO_CREATE)) {
            throw new KeyChainException("could not bind to external authticator app: " + packagename);
        }
        return new ExternalAuthProviderConnection(context, extAuthServiceConnection, q.take());
    }

    private static void ensureNotOnMainThread(@NonNull Context context) {
        Looper looper = Looper.myLooper();
        if (looper != null && looper == context.getMainLooper()) {
            throw new IllegalStateException(
                    "calling this from your main thread can lead to deadlock");
        }
    }

    public static class ExternalAuthProvider {

        public String packageName;
        public boolean configurable = false;
        private String label;

        @Override
        public String toString() {
            return label;
        }
    }

    public static class ExternalAuthProviderConnection implements Closeable {
        private final Context context;
        private final ServiceConnection serviceConnection;
        private final ExternalCertificateProvider service;

        protected ExternalAuthProviderConnection(Context context,
                                                 ServiceConnection serviceConnection,
                                                 ExternalCertificateProvider service) {
            this.context = context;
            this.serviceConnection = serviceConnection;
            this.service = service;
        }

        @Override
        public void close() {
            context.unbindService(serviceConnection);
        }

        public ExternalCertificateProvider getService() {
            return service;
        }
    }
}
