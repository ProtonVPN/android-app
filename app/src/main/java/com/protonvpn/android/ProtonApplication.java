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

import static com.protonvpn.android.utils.AndroidUtilsKt.getAppExitReasonForLog;
import static com.protonvpn.android.utils.PurchaseHandlerKt.initPurchaseHandler;
import static kotlinx.coroutines.CoroutineScopeKt.MainScope;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.protonvpn.android.api.DohEnabled;
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager;
import com.protonvpn.android.managed.AutoLoginManager;
import com.protonvpn.android.auth.usecase.CloseSessionOnForceLogout;
import com.protonvpn.android.auth.usecase.LogoutOnForceUpdate;
import com.protonvpn.android.logging.CurrentStateLogger;
import com.protonvpn.android.logging.CurrentStateLoggerGlobal;
import com.protonvpn.android.logging.FileLogWriter;
import com.protonvpn.android.logging.GlobalSentryLogWriter;
import com.protonvpn.android.logging.LogEventsKt;
import com.protonvpn.android.logging.LogWriter;
import com.protonvpn.android.logging.LogcatLogWriter;
import com.protonvpn.android.logging.PowerStateLogger;
import com.protonvpn.android.logging.ProtonLogger;
import com.protonvpn.android.logging.ProtonLoggerImpl;
import com.protonvpn.android.logging.SettingChangesLogger;
import com.protonvpn.android.notifications.NotificationHelper;
import com.protonvpn.android.notifications.NotificationPermissionManager;
import com.protonvpn.android.quicktile.QuickTileDataStoreUpdater;
import com.protonvpn.android.redesign.recents.usecases.ConnectingUpdatesRecents;
import com.protonvpn.android.redesign.recents.usecases.RecentsListValidator;
import com.protonvpn.android.redesign.upgrade.usecase.PurchasesEnabledUpdater;
import com.protonvpn.android.servers.UpdateServersOnStartAndLocaleChange;
import com.protonvpn.android.telemetry.VpnConnectionTelemetry;
import com.protonvpn.android.tv.IsTvCheck;
import com.protonvpn.android.ui.onboarding.ReviewTracker;
import com.protonvpn.android.ui.planupgrade.ShowUpgradeSuccess;
import com.protonvpn.android.ui.promooffers.OneTimePopupNotificationTrigger;
import com.protonvpn.android.utils.AndroidUtilsKt;
import com.protonvpn.android.utils.ProtonPreferencesMigrationKt;
import com.protonvpn.android.utils.SentryIntegration;
import com.protonvpn.android.utils.Storage;
import com.protonvpn.android.utils.VpnCoreLogger;
import com.protonvpn.android.vpn.CertificateRepository;
import com.protonvpn.android.vpn.LogcatLogCapture;
import com.protonvpn.android.vpn.MaintenanceTracker;
import com.protonvpn.android.vpn.UpdateSettingsOnFeatureFlagChange;
import com.protonvpn.android.vpn.UpdateSettingsOnVpnUserChange;
import com.protonvpn.android.vpn.VpnConnectionObservability;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;
import go.Seq;
import kotlinx.coroutines.ExecutorsKt;
import me.proton.core.accountmanager.data.AccountStateHandler;
import me.proton.core.eventmanager.data.CoreEventManagerStarter;
import me.proton.core.humanverification.presentation.HumanVerificationStateHandler;
import me.proton.core.plan.data.PurchaseStateHandler;
import me.proton.core.userrecovery.presentation.compose.DeviceRecoveryHandler;
import me.proton.core.userrecovery.presentation.compose.DeviceRecoveryNotificationSetup;
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
        AutoLoginManager getAutoLoginManager();
        CertificateRepository getCertificateRepository();
        CloseSessionOnForceLogout getCloseSessionOnForceLogout();
        ConnectingUpdatesRecents getConnectingUpdatesRecents();
        CoreEventManagerStarter getCoreEventManagerStarter();
        CurrentStateLogger getCurrentStateLogger();
        DeviceRecoveryHandler getDeviceRecoveryHandler();
        DeviceRecoveryNotificationSetup getDeviceRecoveryNotificationSetup();
        DohEnabled.Provider getDohEnabledProvider();
        HumanVerificationStateHandler getHumanVerificationStateHandler();
        IsTvCheck getIsTv();
        LogcatLogCapture getLogcatLogCapture();
        LogoutOnForceUpdate getLogoutOnForceUpdate();
        MaintenanceTracker getMaintenanceTracker();
        OneTimePopupNotificationTrigger getOneTimePopupNotificationTrigger();
        PeriodicUpdateManager getPeriodicUpdateManager();
        PowerStateLogger getPowerStateLogger();
        PurchasesEnabledUpdater getPurchasesEnabledUpdater();
        PurchaseStateHandler getPurchaseStateHandler();
        QuickTileDataStoreUpdater getQuickTileDataStoreUpdater();
        RecentsListValidator getRecentsValidator();
        ReviewTracker getReviewTracker();
        SettingChangesLogger getSettingChangesLogger();
        NotificationPermissionManager getNotificationPermissionManager();
        ShowUpgradeSuccess getShowUpgradeSuccess();
        UpdateServersOnStartAndLocaleChange getUpdateServersOnLocaleChange();
        UpdateSettingsOnVpnUserChange getUpdateSettingsOnVpnUserChange();
        UpdateSettingsOnFeatureFlagChange getUpdateSettingsOnFeatureFlagChange();
        VpnConnectionObservability getVpnConnectionObservability();
        VpnConnectionTelemetry getVpnConnectionTelemetry();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = this;

        initPreferences();
        SentryIntegration.initSentry(this);

        if (AndroidUtilsKt.isMainProcess(this)) {
            initLogger();
            String exitReason = getAppExitReasonForLog(this);
            ProtonLogger.INSTANCE.log(
                    LogEventsKt.AppProcessStart,
                    "version: " + BuildConfig.VERSION_NAME + ((exitReason != null) ? "; last exit cause: " + exitReason : "")
            );

            NotificationHelper.Companion.initNotificationChannel(this);

            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

            // Initialize go-libraries early
            Seq.touch();

            CoreLogger.INSTANCE.set(new VpnCoreLogger());
        }
    }

    public void initDependencies() {
        DependencyEntryPoints dependencies = EntryPointAccessors.fromApplication(this, DependencyEntryPoints.class);

        // Start the EventLoop for all logged in Users.
        dependencies.getCoreEventManagerStarter().start();

        // Logging
        dependencies.getCurrentStateLogger().logCurrentState();
        dependencies.getLogcatLogCapture();
        dependencies.getPowerStateLogger();
        dependencies.getSettingChangesLogger();

        dependencies.getAccountStateHandler().start();
        dependencies.getAutoLoginManager();
        dependencies.getCertificateRepository();
        dependencies.getConnectingUpdatesRecents();
        dependencies.getCloseSessionOnForceLogout();
        dependencies.getDeviceRecoveryHandler().start();
        dependencies.getDeviceRecoveryNotificationSetup().init();
        dependencies.getDohEnabledProvider();
        dependencies.getHumanVerificationStateHandler().observe();
        dependencies.getLogoutOnForceUpdate();
        dependencies.getMaintenanceTracker();
        dependencies.getNotificationPermissionManager();
        dependencies.getQuickTileDataStoreUpdater().start();
        dependencies.getPurchasesEnabledUpdater().start();
        dependencies.getPurchaseStateHandler().start();
        dependencies.getRecentsValidator();
        dependencies.getReviewTracker();
        dependencies.getUpdateServersOnLocaleChange();
        dependencies.getUpdateSettingsOnVpnUserChange();
        dependencies.getUpdateSettingsOnFeatureFlagChange();
        dependencies.getShowUpgradeSuccess();
        dependencies.getVpnConnectionObservability();
        dependencies.getVpnConnectionTelemetry().start();

        // Start last.
        dependencies.getPeriodicUpdateManager().start();

        dependencies.getIsTv().logDebugInfo();
        if (!dependencies.getIsTv().invoke()) {
            dependencies.getOneTimePopupNotificationTrigger();
        }
        initPurchaseHandler(this);
    }

    private void initPreferences() {
        String storagePrefsName = "Storage";
        ProtonPreferencesMigrationKt.migrateProtonPreferences(this, "Proton-Secured", storagePrefsName);
        SharedPreferences preferences = getSharedPreferences(storagePrefsName, Context.MODE_PRIVATE);
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
        if (BuildConfig.DEBUG || BuildConfig.ALLOW_LOGCAT) {
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

    private static Context appContext;

    @NotNull
    public static Context getAppContext() {
        return appContext;
    }

    public static void setAppContextForTest(@NotNull Context context) {
        appContext = context;
    }
}
