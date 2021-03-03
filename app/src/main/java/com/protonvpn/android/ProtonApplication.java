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

import android.content.Context;

import com.datatheorem.android.trustkit.TrustKit;
import com.evernote.android.state.StateSaver;
import com.getkeepsafe.relinker.ReLinker;
import com.github.anrwatchdog.ANRWatchDog;
import com.protonvpn.android.components.NotificationHelper;
import com.protonvpn.android.di.DaggerAppComponent;
import com.protonvpn.android.migration.NewAppMigrator;
import com.protonvpn.android.utils.AndroidUtils;
import com.protonvpn.android.utils.ProtonLogger;
import com.protonvpn.android.utils.ProtonPreferences;
import com.protonvpn.android.utils.Storage;
import com.protonvpn.android.vpn.ikev2.StrongswanCertificateManager;

import net.danlew.android.joda.JodaTimeAndroid;

import org.jetbrains.annotations.NotNull;
import org.strongswan.android.logic.StrongSwanApplication;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;
import dagger.android.AndroidInjector;
import dagger.android.support.DaggerApplication;
import io.sentry.Sentry;
import io.sentry.android.AndroidSentryClientFactory;
import leakcanary.AppWatcher;
import rx_activity_result2.RxActivityResult;

public class ProtonApplication extends DaggerApplication implements LifecycleObserver {

    @Override
    public void onCreate() {
        super.onCreate();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        initSentry();
        initStrongSwan();
        NotificationHelper.Companion.initNotificationChannel(this);
        JodaTimeAndroid.init(this);
        TrustKit.initializeWithNetworkSecurityConfiguration(this);
        new ANRWatchDog(15000).start();

        initPreferences();
        NewAppMigrator.INSTANCE.migrate(this);

        RxActivityResult.register(this);
        StateSaver.setEnabledForAllActivitiesAndSupportFragments(this, true);

        if (BuildConfig.DEBUG)
            initLeakCanary();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onBackground() {
        ProtonLogger.INSTANCE.log("App in background");
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onForeground() {
        ProtonLogger.INSTANCE.log("App in foreground");
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

    private void initSentry() {
        String sentryDsn = BuildConfig.DEBUG ? null : BuildConfig.Sentry_DSN;
        Sentry.init(sentryDsn, new AndroidSentryClientFactory(this));
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

    @Override
    protected AndroidInjector<? extends DaggerApplication> applicationInjector() {
        return DaggerAppComponent.builder().application(this).build();
    }

    @NotNull
    public static Context getAppContext() {
        return StrongSwanApplication.getContext();
    }

    public static void setAppContextForTest(@NotNull Context context) {
        StrongSwanApplication.setContext(context);
    }
}