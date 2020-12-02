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
import android.util.Log;

import com.datatheorem.android.trustkit.TrustKit;
import com.evernote.android.state.StateSaver;
import com.getkeepsafe.relinker.ReLinker;
import com.github.anrwatchdog.ANRWatchDog;
import com.protonvpn.android.components.NotificationHelper;
import com.protonvpn.android.di.DaggerAppComponent;
import com.protonvpn.android.migration.NewAppMigrator;
import com.protonvpn.android.utils.AndroidUtils;
import com.protonvpn.android.utils.ProtonPreferences;
import com.protonvpn.android.utils.Storage;

import net.danlew.android.joda.JodaTimeAndroid;

import org.strongswan.android.logic.StrongSwanApplication;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDex;
import dagger.android.AndroidInjector;
import dagger.android.support.DaggerApplication;
import io.sentry.Sentry;
import io.sentry.android.AndroidSentryClientFactory;
import leakcanary.AppWatcher;
import rx_activity_result2.RxActivityResult;

public class ProtonApplication extends StrongSwanApplication {

    private void initLibraries() {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        try {
            ReLinker.loadLibrary(ProtonApplication.getAppContext(), "androidbridge");
        }
        catch (Exception e) {
            Log.e("Native", "could not load openpgp library", e);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public static Context getAppContext() {
        return getContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initSentry();
        NotificationHelper.INSTANCE.initNotificationChannel(this);
        JodaTimeAndroid.init(this);
        initLibraries();
        TrustKit.initializeWithNetworkSecurityConfiguration(this);
        new ANRWatchDog(15000).start();

        initPreferences();
        NewAppMigrator.INSTANCE.migrate(this);

        RxActivityResult.register(this);
        StateSaver.setEnabledForAllActivitiesAndSupportFragments(this, true);

        if (BuildConfig.DEBUG)
            initLeakCanary();
    }

    private void initPreferences() {
        ProtonPreferences preferences =
            new ProtonPreferences(this, BuildConfig.PREF_SALT, BuildConfig.PREF_KEY, "Proton-Secured");
        Storage.setPreferences(preferences);
    }

    private void initSentry() {
        String sentryDsn = BuildConfig.DEBUG ? null : BuildConfig.Sentry_DSN;
        Sentry.init(sentryDsn, new AndroidSentryClientFactory(getContext()));
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

}