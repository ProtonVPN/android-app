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

import android.app.BackgroundServiceStartNotAllowedException
import android.os.Build
import android.util.AndroidRuntimeException
import androidx.annotation.VisibleForTesting
import io.sentry.SentryEvent

object SentryFingerprints {

    // Replace ID of a service in exception message, e.g.:
    //   ... did not then call Service.startForeground(): ServiceRecord{d52c9c5 u0 ...
    //   ... did not then call Service.startForeground(): ServiceRecord{<id> u0 ...
    private val serviceRecordIdRegex = Regex("ServiceRecord\\{[a-fA-F0-9]+")
    private const val serviceRecordReplacement = "ServiceRecord{<id>"

    // Replace numerical values, e.g.:
    //   Bad notification(tag=null, id=6) posted from package ..., crashing app(uid=10351, pid=13861) ...
    //   Bad notification(tag=<num>, id=<num>) posted from package ..., crashing app(uid=<num>, pid=<num>) ...
    private val numberValueRegex = Regex("\\b(\\d+|null)\\b")
    private const val numberValueReplacement = "<num>"

    fun setFingerprints(event: SentryEvent): SentryEvent {
        val throwable = event.throwable
        if (
            // RemoteServiceException and ForegroundServiceDidNotStartInTimeException are private
            // so they can't be handled explicitly. AndroidRuntimeException is their public super
            // class. It shouldn't hurt to apply the same grouping to its other subclasses.
            throwable is AndroidRuntimeException ||
            // Group BackgroundServiceStartNotAllowedExceptions by service name.
            Build.VERSION.SDK_INT >= 31 && throwable is BackgroundServiceStartNotAllowedException
        ) {
            // Add the exception message to fingerprint so that different causes are grouped
            // separately by Sentry. Unique parts like IDs are removed from the message.
            event.fingerprints = listOf(
                "{{ default }}",
                throwable.message?.let { removeUniquesFromMessage(it) }
            )
        }
        return event
    }

    @VisibleForTesting
    fun removeUniquesFromMessage(message: String): String =
        message
            .replace(serviceRecordIdRegex, serviceRecordReplacement)
            .replace(numberValueRegex, numberValueReplacement)
}
