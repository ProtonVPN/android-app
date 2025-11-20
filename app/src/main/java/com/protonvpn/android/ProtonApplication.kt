/*
 * Copyright (c) 2017 Proton AG
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
package com.protonvpn.android

import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import com.protonvpn.android.api.DohEnabled
import com.protonvpn.android.app.AppExitObservability
import com.protonvpn.android.app.AppStartExitLogger
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.auth.usecase.CloseSessionOnForceLogout
import com.protonvpn.android.auth.usecase.LogoutOnForceUpdate
import com.protonvpn.android.logging.AppProcessStart
import com.protonvpn.android.logging.CurrentStateLogger
import com.protonvpn.android.logging.CurrentStateLoggerGlobal
import com.protonvpn.android.logging.FileLogWriter
import com.protonvpn.android.logging.GlobalSentryLogWriter
import com.protonvpn.android.logging.LogWriter
import com.protonvpn.android.logging.LogcatLogWriter
import com.protonvpn.android.logging.PowerStateLogger
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.ProtonLoggerImpl
import com.protonvpn.android.logging.SettingChangesLogger
import com.protonvpn.android.managed.AutoLoginManager
import com.protonvpn.android.notifications.NotificationHelper.Companion.initNotificationChannel
import com.protonvpn.android.notifications.NotificationPermissionManager
import com.protonvpn.android.profiles.usecases.PopulateInitialProfiles
import com.protonvpn.android.profiles.usecases.ProfileAutoOpenHandler
import com.protonvpn.android.profiles.usecases.UpdateProfileLastConnected
import com.protonvpn.android.quicktile.QuickTileDataStoreUpdater
import com.protonvpn.android.redesign.recents.usecases.ConnectingUpdatesRecents
import com.protonvpn.android.redesign.recents.usecases.RecentsListValidator
import com.protonvpn.android.redesign.upgrade.usecase.PurchasesEnabledUpdater
import com.protonvpn.android.servers.UpdateServersOnStartAndLocaleChange
import com.protonvpn.android.telemetry.VpnConnectionTelemetry
import com.protonvpn.android.theme.UpdateAndroidAppTheme
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.ui.onboarding.ReviewTracker
import com.protonvpn.android.ui.planupgrade.ShowUpgradeSuccess
import com.protonvpn.android.ui.promooffers.OneTimePopupNotificationTrigger
import com.protonvpn.android.utils.SentryIntegration.initSentry
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.VpnCoreLogger
import com.protonvpn.android.utils.initPurchaseHandler
import com.protonvpn.android.utils.isMainProcess
import com.protonvpn.android.utils.migrateProtonPreferences
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.LogcatLogCapture
import com.protonvpn.android.vpn.MaintenanceTracker
import com.protonvpn.android.vpn.UpdateSettingsOnFeatureFlagChange
import com.protonvpn.android.vpn.UpdateSettingsOnVpnUserChange
import com.protonvpn.android.vpn.VpnConnectionObservability
import com.protonvpn.android.vpn.autoconnect.AutoConnectBootReceiverController
import com.protonvpn.android.widget.WidgetStateUpdater
import com.protonvpn.android.widget.data.WidgetTracker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import me.proton.core.accountmanager.data.AccountStateHandler
import me.proton.core.eventmanager.data.CoreEventManagerStarter
import me.proton.core.humanverification.presentation.HumanVerificationStateHandler
import me.proton.core.network.presentation.installCertificateTransparencySupport
import me.proton.core.plan.data.PurchaseStateHandler
import me.proton.core.userrecovery.presentation.compose.DeviceRecoveryHandler
import me.proton.core.userrecovery.presentation.compose.DeviceRecoveryNotificationSetup
import me.proton.core.util.kotlin.CoreLogger
import java.util.concurrent.Executors

/**
 * Base Application for both the real application and application for use in instrumented tests.
 *
 * It introduces initDependencies() method that can access Hilt dependencies via EntryPoints.
 *
 * The method is either called by the subclass for the real application or, in tests, by
 * ProtonHiltAndroidRule (which replaces HiltAndroidRule).
 */
open class ProtonApplication : Application() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface DependencyEntryPoints {
        val accountStateHandler: AccountStateHandler
        val appExitObservability: AppExitObservability
        val appStartExitLogger: AppStartExitLogger
        val autoConnectBootController: AutoConnectBootReceiverController
        val autoLoginManager: AutoLoginManager?
        val certificateRepository: CertificateRepository?
        val closeSessionOnForceLogout: CloseSessionOnForceLogout?
        val connectingUpdatesRecents: ConnectingUpdatesRecents?
        val coreEventManagerStarter: CoreEventManagerStarter
        val currentStateLogger: CurrentStateLogger
        val deviceRecoveryHandler: DeviceRecoveryHandler
        val deviceRecoveryNotificationSetup: DeviceRecoveryNotificationSetup
        val dohEnabledProvider: DohEnabled.Provider?
        val humanVerificationStateHandler: HumanVerificationStateHandler
        val isTv: IsTvCheck
        val logcatLogCapture: LogcatLogCapture?
        val logoutOnForceUpdate: LogoutOnForceUpdate?
        val maintenanceTracker: MaintenanceTracker?
        val oneTimePopupNotificationTrigger: OneTimePopupNotificationTrigger?
        val periodicUpdateManager: PeriodicUpdateManager
        val powerStateLogger: PowerStateLogger?
        val purchasesEnabledUpdater: PurchasesEnabledUpdater
        val purchaseStateHandler: PurchaseStateHandler
        val quickTileDataStoreUpdater: QuickTileDataStoreUpdater
        val recentsValidator: RecentsListValidator?
        val reviewTracker: ReviewTracker?
        val settingChangesLogger: SettingChangesLogger?
        val notificationPermissionManager: NotificationPermissionManager?
        val showUpgradeSuccess: ShowUpgradeSuccess?
        val updateAndroidAppTheme: UpdateAndroidAppTheme
        val updateProfileLastConnected: UpdateProfileLastConnected
        val updateServersOnLocaleChange: UpdateServersOnStartAndLocaleChange?
        val updateSettingsOnVpnUserChange: UpdateSettingsOnVpnUserChange?
        val updateSettingsOnFeatureFlagChange: UpdateSettingsOnFeatureFlagChange?
        val vpnConnectionObservability: VpnConnectionObservability?
        val vpnConnectionTelemetry: VpnConnectionTelemetry
        val widgetStateUpdater: WidgetStateUpdater
        val widgetTracker: WidgetTracker
        val goLangCrashReporter: dagger.Lazy<GoLangCrashReporter>
        val populateInitialProfiles: PopulateInitialProfiles
        val profileAutoOpenHandler: ProfileAutoOpenHandler
    }

    protected var lastMainProcessExitReason: Int? = null

    override fun onCreate() {
        installCertificateTransparencySupport(
            excludedCommonNames = if (BuildConfig.DEBUG) listOf("localhost") else emptyList()
        )

        super.onCreate()
        appContext = this

        initPreferences()
        initSentry(this)

        if (isMainProcess()) {
            VpnLeakCanary.init(this)
            initLogger()
            ProtonLogger.log(AppProcessStart, "version: " + BuildConfig.VERSION_NAME)

            initNotificationChannel(this)

            CoreLogger.set(VpnCoreLogger())
        }
    }

    fun initDependencies() {
        val dependencies = EntryPointAccessors.fromApplication(this, DependencyEntryPoints::class.java)

        dependencies.updateAndroidAppTheme.start() // Set UI theme early.

        // Start the EventLoop for all logged in Users.
        dependencies.coreEventManagerStarter.start()

        // Logging
        dependencies.appStartExitLogger.log()
        dependencies.currentStateLogger.logCurrentState()
        dependencies.logcatLogCapture
        dependencies.powerStateLogger
        dependencies.settingChangesLogger

        dependencies.accountStateHandler.start()
        dependencies.appExitObservability.start()
        dependencies.autoConnectBootController.start()
        dependencies.autoLoginManager
        dependencies.certificateRepository
        dependencies.connectingUpdatesRecents
        dependencies.closeSessionOnForceLogout
        dependencies.deviceRecoveryHandler.start()
        dependencies.deviceRecoveryNotificationSetup.init()
        dependencies.dohEnabledProvider
        dependencies.humanVerificationStateHandler.observe()
        dependencies.logoutOnForceUpdate
        dependencies.maintenanceTracker
        dependencies.notificationPermissionManager
        dependencies.quickTileDataStoreUpdater.start()
        dependencies.populateInitialProfiles.start()
        dependencies.profileAutoOpenHandler.start()
        dependencies.purchasesEnabledUpdater.start()
        dependencies.purchaseStateHandler.start()
        dependencies.recentsValidator
        dependencies.reviewTracker
        dependencies.updateProfileLastConnected.start()
        dependencies.updateServersOnLocaleChange
        dependencies.updateSettingsOnVpnUserChange
        dependencies.updateSettingsOnFeatureFlagChange
        dependencies.showUpgradeSuccess
        dependencies.vpnConnectionObservability
        dependencies.vpnConnectionTelemetry.start()
        dependencies.widgetStateUpdater.start()
        dependencies.widgetTracker.start()

        // Start last.
        dependencies.periodicUpdateManager.start()

        dependencies.isTv.logDebugInfo()
        if (!dependencies.isTv()) {
            dependencies.oneTimePopupNotificationTrigger
        }
        initPurchaseHandler(this)

        if (lastMainProcessExitReason in listOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE)) {
            dependencies.goLangCrashReporter.get().start()
        }
    }

    private fun initPreferences() {
        val storagePrefsName = "Storage"
        migrateProtonPreferences(this, "Proton-Secured", storagePrefsName)
        val preferences = getSharedPreferences(storagePrefsName, MODE_PRIVATE)
        Storage.setPreferences(preferences)
    }

    private fun initLogger() {
        val secondaryWriters: MutableList<LogWriter> = ArrayList()
        if (this is ProtonApplicationHilt) {
            // Add GlobalSentryLogWriter only in real application, it doesn't work with Hilt tests
            // because some message are being logged already in ProtonApplication.onCreate() - Hilt
            // dependencies are not available in tests this early.
            secondaryWriters.add(GlobalSentryLogWriter(this))
        }
        if (BuildConfig.DEBUG || BuildConfig.ALLOW_LOGCAT) {
            secondaryWriters.add(LogcatLogWriter())
        }

        ProtonLogger.setLogger(
            ProtonLoggerImpl(
                { System.currentTimeMillis() },
                FileLogWriter(
                    getAppContext(),
                    MainScope(),
                    Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
                    applicationInfo.dataDir + "/log",
                    CurrentStateLoggerGlobal(this)
                ),
                secondaryWriters
            )
        )
    }

    companion object {
        private var appContext: Context? = null

        fun getAppContext(): Context {
            return appContext!!
        }

        fun setAppContextForTest(context: Context) {
            appContext = context
        }
    }
}
