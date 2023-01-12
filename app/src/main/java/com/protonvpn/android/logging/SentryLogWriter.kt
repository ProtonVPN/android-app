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

package com.protonvpn.android.logging

import android.content.Context
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.utils.NetUtils.maskAnyIP
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import me.proton.core.user.domain.entity.User
import me.proton.core.util.kotlin.takeIfNotBlank
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_MESSAGE_LENGTH = 400

/**
 * A bridge to use SentryLogWriter with Hilt-injected dependencies in ProtonLogger.
 */
class GlobalSentryLogWriter(appContext: Context) : LogWriter {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HiltHelper {
        fun sentryLogWriter(): SentryLogWriter
    }

    private val sentryLogWriter = EntryPoints.get(appContext, HiltHelper::class.java).sentryLogWriter()

    override fun write(
        timestamp: String,
        level: LogLevel,
        category: LogCategory,
        eventName: String?,
        message: String,
        blocking: Boolean
    ) {
        sentryLogWriter.write(timestamp, level, category, eventName, message, blocking)
    }
}

@Singleton
class SentryLogWriter @Inject constructor(
    mainScope: CoroutineScope,
    currentUser: CurrentUser
) : LogWriter {

    private val cachedUser: StateFlow<User?> = currentUser.userFlow.stateIn(
        mainScope, SharingStarted.Eagerly, initialValue = null
    )

    override fun write(
        timestamp: String,
        level: LogLevel,
        category: LogCategory,
        eventName: String?,
        message: String,
        blocking: Boolean
    ) {
        if (
            category == LogCategory.PROTOCOL ||
            level <= LogLevel.DEBUG ||
            eventName == AppCrash.name
        ) return

        Sentry.addBreadcrumb(
            Breadcrumb().apply {
                val eventPart = eventName?.let { ":$it" }.orEmpty()
                this.category = "${category.toLog()}$eventPart"
                this.level = level.toSentryLevel()
                this.message = scrubMessage(message.take(MAX_MESSAGE_LENGTH))
            }
        )
    }

    private fun scrubMessage(message: String): String {
        var scrubbed = message.maskAnyIP()
        val user = cachedUser.value
        user?.name?.takeIfNotBlank()?.let { scrubbed = scrubbed.replace(it, "<username>") }
        user?.displayName?.takeIfNotBlank()?.let { scrubbed = scrubbed.replace(it, "<username>") }
        user?.email?.takeIfNotBlank()?.let { scrubbed = scrubbed.replace(it, "<email>") }
        return scrubbed
    }

    private fun LogLevel.toSentryLevel() = when (this) {
        LogLevel.TRACE -> SentryLevel.DEBUG
        LogLevel.DEBUG -> SentryLevel.DEBUG
        LogLevel.INFO -> SentryLevel.INFO
        LogLevel.WARN -> SentryLevel.WARNING
        LogLevel.ERROR -> SentryLevel.ERROR
        LogLevel.FATAL -> SentryLevel.FATAL
    }
}
