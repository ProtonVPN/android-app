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

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import com.datatheorem.android.trustkit.TrustKit;
import com.evernote.android.state.StateSaver;
import com.getkeepsafe.relinker.ReLinker;
import com.protonvpn.android.components.NotificationHelper;
import com.protonvpn.android.logging.LogEventsKt;
import com.protonvpn.android.logging.ProtonLogger;
import com.protonvpn.android.utils.AndroidUtils;
import com.protonvpn.android.utils.DefaultActivityLifecycleCallbacks;
import com.protonvpn.android.utils.ProtonPreferences;
import com.protonvpn.android.utils.SentryIntegration;
import com.protonvpn.android.utils.Storage;
import com.protonvpn.android.utils.VpnCoreLogger;
import com.protonvpn.android.vpn.ikev2.StrongswanCertificateManager;

import net.danlew.android.joda.JodaTimeAndroid;

import org.jetbrains.annotations.NotNull;
import org.strongswan.android.logic.StrongSwanApplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import go.Seq;
import leakcanary.AppWatcher;
import me.proton.core.util.kotlin.CoreLogger;

public class ProtonApplication extends Application {

    public Activity foregroundActivity;

    @Override
    public void onCreate() {
        super.onCreate();
        initActivityObserver();
        initPreferences();
        SentryIntegration.initSentry(this);
        initStrongSwan();

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
        registerActivityLifecycleCallbacks(new DefaultActivityLifecycleCallbacks() {

            @Override public void onActivityResumed(@NonNull Activity activity) {
                foregroundActivity = activity;
                ProtonLogger.logActivityResumed(activity);
            }

            @Override public void onActivityPaused(@NonNull Activity activity) {
                foregroundActivity = null;
                ProtonLogger.logActivityPaused(activity);
            }
        });
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
        return foregroundActivity != null;
    }
}
