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
package com.protonvpn.android;

import static kotlinx.coroutines.CoroutineScopeKt.MainScope;

import android.app.Application;
import android.content.Context;

import com.datatheorem.android.trustkit.TrustKit;
import com.evernote.android.state.StateSaver;
import com.getkeepsafe.relinker.ReLinker;
import com.protonvpn.android.auth.usecase.CoreLoginMigration;
import com.protonvpn.android.components.NotificationHelper;
import com.protonvpn.android.logging.CurrentStateLogger;
import com.protonvpn.android.logging.CurrentStateLoggerGlobal;
import com.protonvpn.android.logging.FileLogWriter;
import com.protonvpn.android.logging.GlobalSentryLogWriter;
import com.protonvpn.android.logging.LogEventsKt;
import com.protonvpn.android.logging.LogWriter;
import com.protonvpn.android.logging.LogcatLogWriter;
import com.protonvpn.android.logging.ProtonLogger;
import com.protonvpn.android.logging.ProtonLoggerImpl;
import com.protonvpn.android.logging.SettingChangesLogger;
import com.protonvpn.android.search.UpdateServersOnLocaleChange;
import com.protonvpn.android.utils.ProtonPreferences;
import com.protonvpn.android.utils.SentryIntegration;
import com.protonvpn.android.utils.Storage;
import com.protonvpn.android.utils.VpnCoreLogger;
import com.protonvpn.android.vpn.CertificateRepository;
import com.protonvpn.android.vpn.LogcatLogCapture;
import com.protonvpn.android.vpn.UpdateSettingsOnVpnUserChange;
import com.protonvpn.android.vpn.MaintenanceTracker;
import com.protonvpn.android.vpn.ikev2.StrongswanCertificateManager;

import net.danlew.android.joda.JodaTimeAndroid;

import org.jetbrains.annotations.NotNull;
import org.strongswan.android.logic.StrongSwanApplication;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;
import go.Seq;
import kotlinx.coroutines.ExecutorsKt;
import leakcanary.AppWatcher;
import me.proton.core.accountmanager.data.AccountStateHandler;
import me.proton.core.util.kotlin.CoreLogger;

/**
 * Base Application for both the real application and application for use in instrumented tests.
 *
 * It introduces initDependencies() method that can access Hilt dependencies via EntryPoints.
 *
 * The method is either called by the subclass for the real application or, in tests, by
 * ProtonHiltAndroidRule (which replaces HiltAndroidRule).
 */
public class ProtonApplication extends Application {

    @EntryPoint
    @InstallIn(SingletonComponent.class)
    interface DependencyEntryPoints {
        AccountStateHandler getAccountStateHandler();
        CertificateRepository getCertificateRepository();
        CoreLoginMigration getCoreLoginMigration();
        CurrentStateLogger getCurrentStateLogger();
        LogcatLogCapture getLogcatLogCapture();
        MaintenanceTracker getMaintenanceTracker();
        SettingChangesLogger getSettingChangesLogger();
        UpdateSettingsOnVpnUserChange getUpdateSettingsOnVpnUserChange();
        UpdateServersOnLocaleChange getUpdateServersOnLocaleChange();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initPreferences();
        SentryIntegration.initSentry(this);
        initStrongSwan();

        initLogger();
        ProtonLogger.INSTANCE.log(LogEventsKt.AppProcessStart, "version: " + BuildConfig.VERSION_NAME);

        NotificationHelper.Companion.initNotificationChannel(this);
        JodaTimeAndroid.init(this);
        TrustKit.initializeWithNetworkSecurityConfiguration(this);

        StateSaver.setEnabledForAllActivitiesAndSupportFragments(this, true);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        // Initialize go-libraries early to avoid crashes in StrongSwan
        Seq.touch();

        boolean isUpdated = handleUpdate();
        if (isUpdated) {
            ProtonLogger.INSTANCE.log(
                    LogEventsKt.AppUpdateUpdated, "new version: " + BuildConfig.VERSION_NAME);
        }

        CoreLogger.INSTANCE.set(new VpnCoreLogger());
    }

    public void initDependencies() {
        DependencyEntryPoints dependencies = EntryPointAccessors.fromApplication(this, DependencyEntryPoints.class);

        // Migrate before anything else that uses the AccountManager.
        dependencies.getCoreLoginMigration().migrateIfNeeded();

        // Logging
        dependencies.getCurrentStateLogger().logCurrentState();
        dependencies.getLogcatLogCapture();
        dependencies.getSettingChangesLogger();

        dependencies.getAccountStateHandler().start();
        dependencies.getCertificateRepository();
        dependencies.getMaintenanceTracker();
        dependencies.getUpdateSettingsOnVpnUserChange();
        dependencies.getUpdateServersOnLocaleChange();
    }

    private void initStrongSwan() {
        ReLinker.loadLibrary(this, "androidbridge");

        // Static blocks from StrongSwanApplication will execute here loading native library and initializing
        // certificate store.
        StrongSwanApplication.setContext(getApplicationContext());
        StrongswanCertificateManager.INSTANCE.init(getBaseContext());
    }

    private void initPreferences() {
        ProtonPreferences preferences =
            new ProtonPreferences(this, BuildConfig.PREF_SALT, BuildConfig.PREF_KEY, "Proton-Secured");
        Storage.setPreferences(preferences);
    }

    private void initLogger() {
        List<LogWriter> secondaryWriters = new ArrayList<>();
        if (this instanceof ProtonApplicationHilt) {
            // Add GlobalSentryLogWriter only in real application, it doesn't work with Hilt tests
            // because some message are being logged already in ProtonApplication.onCreate() - Hilt
            // dependencies are not available in tests this early.
            secondaryWriters.add(new GlobalSentryLogWriter(this));
        }
        if (BuildConfig.DEBUG) {
            secondaryWriters.add(new LogcatLogWriter());
        }

        ProtonLogger.setLogger(new ProtonLoggerImpl(
                System::currentTimeMillis,
                new FileLogWriter(
                        ProtonApplication.getAppContext(),
                        MainScope(),
                        ExecutorsKt.from(Executors.newSingleThreadExecutor()),
                        getApplicationInfo().dataDir + "/log",
                        new CurrentStateLoggerGlobal(this)),
                secondaryWriters));
    }

    private boolean handleUpdate() {
        int versionCode = Storage.getInt("VERSION_CODE");
        Storage.saveInt("VERSION_CODE", BuildConfig.VERSION_CODE);
        return versionCode != 0 && versionCode != BuildConfig.VERSION_CODE;
    }

    @NotNull
    public static Context getAppContext() {
        return StrongSwanApplication.getContext();
    }

    public static void setAppContextForTest(@NotNull Context context) {
        StrongSwanApplication.setContext(context);
    }
}
