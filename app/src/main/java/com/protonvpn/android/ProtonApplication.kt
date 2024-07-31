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
package com.protonvpn.android

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.protonvpn.android.api.DohEnabled
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
import com.protonvpn.android.quicktile.QuickTileDataStoreUpdater
import com.protonvpn.android.redesign.recents.usecases.ConnectingUpdatesRecents
import com.protonvpn.android.redesign.recents.usecases.RecentsListValidator
import com.protonvpn.android.redesign.upgrade.usecase.PurchasesEnabledUpdater
import com.protonvpn.android.servers.UpdateServersOnStartAndLocaleChange
import com.protonvpn.android.telemetry.VpnConnectionTelemetry
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.ui.onboarding.ReviewTracker
import com.protonvpn.android.ui.planupgrade.ShowUpgradeSuccess
import com.protonvpn.android.ui.promooffers.OneTimePopupNotificationTrigger
import com.protonvpn.android.utils.SentryIntegration.initSentry
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.VpnCoreLogger
import com.protonvpn.android.utils.getAppExitReasonForLog
import com.protonvpn.android.utils.initPurchaseHandler
import com.protonvpn.android.utils.isMainProcess
import com.protonvpn.android.utils.migrateProtonPreferences
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.LogcatLogCapture
import com.protonvpn.android.vpn.MaintenanceTracker
import com.protonvpn.android.vpn.UpdateSettingsOnFeatureFlagChange
import com.protonvpn.android.vpn.UpdateSettingsOnVpnUserChange
import com.protonvpn.android.vpn.VpnConnectionObservability
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors.fromApplication
import dagger.hilt.components.SingletonComponent
import go.Seq
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import me.proton.core.accountmanager.data.AccountStateHandler
import me.proton.core.eventmanager.data.CoreEventManagerStarter
import me.proton.core.humanverification.presentation.HumanVerificationStateHandler
import me.proton.core.plan.data.PurchaseStateHandler
import me.proton.core.userrecovery.presentation.compose.DeviceRecoveryHandler
import me.proton.core.userrecovery.presentation.compose.DeviceRecoveryNotificationSetup
import me.proton.core.util.kotlin.CoreLogger.set
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
        val updateServersOnLocaleChange: UpdateServersOnStartAndLocaleChange?
        val updateSettingsOnVpnUserChange: UpdateSettingsOnVpnUserChange?
        val updateSettingsOnFeatureFlagChange: UpdateSettingsOnFeatureFlagChange?
        val vpnConnectionObservability: VpnConnectionObservability?
        val vpnConnectionTelemetry: VpnConnectionTelemetry
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this

        initPreferences()
        initSentry(this)

        if (isMainProcess()) {
            initLogger()
            val exitReason = this.getAppExitReasonForLog()
            ProtonLogger.log(
                AppProcessStart,
                "version: " + BuildConfig.VERSION_NAME + (if ((exitReason != null)) "; last exit cause: $exitReason" else "")
            )

            initNotificationChannel(this)

            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

            // Initialize go-libraries early
            Seq.touch()

            set(VpnCoreLogger())
        }
    }

    fun initDependencies() {
        val dependencies = fromApplication(this, DependencyEntryPoints::class.java)

        // Start the EventLoop for all logged in Users.
        dependencies.coreEventManagerStarter.start()

        // Logging
        dependencies.currentStateLogger.logCurrentState()
        dependencies.logcatLogCapture
        dependencies.powerStateLogger
        dependencies.settingChangesLogger

        dependencies.accountStateHandler.start()
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
        dependencies.purchasesEnabledUpdater.start()
        dependencies.purchaseStateHandler.start()
        dependencies.recentsValidator
        dependencies.reviewTracker
        dependencies.updateServersOnLocaleChange
        dependencies.updateSettingsOnVpnUserChange
        dependencies.updateSettingsOnFeatureFlagChange
        dependencies.showUpgradeSuccess
        dependencies.vpnConnectionObservability
        dependencies.vpnConnectionTelemetry.start()

        // Start last.
        dependencies.periodicUpdateManager.start()

        dependencies.isTv.logDebugInfo()
        if (!dependencies.isTv.invoke()) {
            dependencies.oneTimePopupNotificationTrigger
        }
        initPurchaseHandler(this)
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
