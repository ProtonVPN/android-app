/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.android.utils

import android.app.Application
import android.os.Build
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.tv.IsTvCheck
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.User
import me.proton.core.util.android.sentry.project.AccountSentryHubBuilder
import java.util.UUID

object SentryIntegration {

    private const val SENTRY_INSTALLATION_ID_KEY = "sentry_installation_id"
    private const val SENTRY_ENABLED_KEY = "sentry_is_enabled"

    private lateinit var application: Application

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HiltEntryPoint {
        fun isTv(): IsTvCheck
    }

    @JvmStatic
    fun getInstallationId(): String =
        Storage.getString(SENTRY_INSTALLATION_ID_KEY, null)
            ?: UUID.randomUUID().toString().also {
                Storage.saveString(SENTRY_INSTALLATION_ID_KEY, it)
            }

    @JvmStatic
    fun initSentry(app: Application) {
        application = app
        initSentry()

        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(ProtonExceptionHandler(currentHandler))
    }

    fun setEnabled(isEnabled: Boolean) {
        Storage.saveBoolean(SENTRY_ENABLED_KEY, isEnabled)
        initSentry()
    }

    fun isEnabled() = Storage.getBoolean(SENTRY_ENABLED_KEY, true)

    private fun initSentry() {
        val sentryDsn = if (!BuildConfig.DEBUG && isEnabled()) BuildConfig.Sentry_DSN else ""
        SentryAndroid.init(application) { options ->
            options.dsn = sentryDsn
            options.release = BuildConfig.VERSION_NAME
            options.isEnableAutoSessionTracking = false
            options.isEnableActivityLifecycleBreadcrumbs = false // We log our own breadcrumbs.
            options.isEnableUserInteractionBreadcrumbs = false
            options.isEnableFramesTracking = false
            options.isAnrEnabled = false
            options.maxBreadcrumbs = 300
            options.setBeforeSend { event, _ ->
                val deps = EntryPointAccessors.fromApplication(application, HiltEntryPoint::class.java)
                SentryFingerprints.setFingerprints(event)
                event.setTag("isTv", deps.isTv().toString())
                event
            }
            options.isEnableScopeSync = true
        }
        Sentry.setUser(User().apply { id = getInstallationId() })
        // Add manufacturer because some devices report device model for "device.family" which isn't very useful for
        // vendor-specific issues.
        Sentry.setTag("device.manufacturer", Build.MANUFACTURER)
    }

    /** Note: should be called only once. */
    fun initAccountSentry() {
        val entryPoint = EntryPointAccessors.fromApplication(
            application,
            SentryIntegrationEntryPoint::class.java
        )

        entryPoint.accountSentryHubBuilder().invoke(
            sentryDsn = BuildConfig.ACCOUNT_SENTRY_DSN.takeIf { !BuildConfig.DEBUG }.orEmpty(),
            installationId = getInstallationId()
        ) { options ->
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                if (isEnabled()) event else null
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface SentryIntegrationEntryPoint {
        fun accountSentryHubBuilder(): AccountSentryHubBuilder
    }
}
