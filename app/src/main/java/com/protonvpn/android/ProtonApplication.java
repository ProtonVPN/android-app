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

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.PowerManager;

import com.datatheorem.android.trustkit.TrustKit;
import com.evernote.android.state.StateSaver;
import com.getkeepsafe.relinker.ReLinker;
import com.protonvpn.android.components.NotificationHelper;
import com.protonvpn.android.logging.CurrentStateLoggerGlobal;
import com.protonvpn.android.logging.FileLogWriter;
import com.protonvpn.android.logging.GlobalSentryLogWriter;
import com.protonvpn.android.logging.LogEventsKt;
import com.protonvpn.android.logging.LogWriter;
import com.protonvpn.android.logging.ProtonLogger;
import com.protonvpn.android.logging.ProtonLoggerImpl;
import com.protonvpn.android.ui.ForegroundActivityTracker;
import com.protonvpn.android.utils.AndroidUtils;
import com.protonvpn.android.utils.ProtonPreferences;
import com.protonvpn.android.utils.SentryIntegration;
import com.protonvpn.android.utils.Storage;
import com.protonvpn.android.utils.VpnCoreLogger;
import com.protonvpn.android.vpn.ikev2.StrongswanCertificateManager;

import net.danlew.android.joda.JodaTimeAndroid;

import org.jetbrains.annotations.NotNull;
import org.strongswan.android.logic.StrongSwanApplication;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import go.Seq;
import kotlinx.coroutines.ExecutorsKt;
import leakcanary.AppWatcher;
import me.proton.core.util.kotlin.CoreLogger;

public class ProtonApplication extends Application {

    @Nullable
    private ForegroundActivityTracker foregroundTracker;

    @Override
    public void onCreate() {
        super.onCreate();
        initActivityObserver();
        initPreferences();
        SentryIntegration.initSentry(this);
        initStrongSwan();

        initLogger();
        ProtonLogger.INSTANCE.log(LogEventsKt.AppProcessStart, "version: " + BuildConfig.VERSION_NAME);

        NotificationHelper.Companion.initNotificationChannel(this);
        JodaTimeAndroid.init(this);
        TrustKit.initializeWithNetworkSecurityConfiguration(this);

        StateSaver.setEnabledForAllActivitiesAndSupportFragments(this, true);

        if (BuildConfig.DEBUG)
            initLeakCanary();

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

    private void initActivityObserver() {
        foregroundTracker =
                new ForegroundActivityTracker((PowerManager) getSystemService(Context.POWER_SERVICE));
        registerActivityLifecycleCallbacks(foregroundTracker);
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

    private void initLeakCanary() {
         if (AndroidUtils.INSTANCE.isTV(this)) {
            // Leanback seems to have issues with leaking fragment views
            AppWatcher.Config config = AppWatcher.getConfig().newBuilder()
                .watchFragmentViews(false)
                .build();
            AppWatcher.setConfig(config);
        }
    }

    private void initLogger() {
        // Add GlobalSentryLogWriter only in real application, it doesn't work with Hilt tests
        // because some message are being logged already in ProtonApplication.onCreate() - Hilt
        // dependencies are not available in tests this early.
        List<LogWriter> secondaryWriters = this instanceof ProtonApplicationHilt
                ? Collections.singletonList(new GlobalSentryLogWriter(this))
                : Collections.emptyList();

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
        return versionCode != BuildConfig.VERSION_CODE;
    }

    @NotNull
    public static Context getAppContext() {
        return StrongSwanApplication.getContext();
    }

    public static void setAppContextForTest(@NotNull Context context) {
        StrongSwanApplication.setContext(context);
    }

    public boolean isInForeground() {
        return foregroundTracker != null && foregroundTracker.isInForeground();
    }

    @Nullable
    public Activity getForegroundActivity() {
        return foregroundTracker != null ? foregroundTracker.foregroundActivity() : null;
    }
}
