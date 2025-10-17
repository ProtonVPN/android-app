/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.servers

import androidx.annotation.WorkerThread
import com.protonvpn.android.appconfig.UserCountryIpBased
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.toLog
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.utils.runCatchingCheckedExceptions
import com.protonvpn.android.utils.stacktraceMessage
import dagger.Reusable
import io.sentry.Sentry
import uniffi.proton_vpn_binary_status.computeLoadsUniffi
import javax.inject.Inject
import uniffi.proton_vpn_binary_status.Location as UniffiLocation
import uniffi.proton_vpn_binary_status.Logical as UniffiLogical
import uniffi.proton_vpn_binary_status.StatusReference as UniffiStatusReference

interface UpdateServersWithBinaryStatus {
    operator fun invoke(serversToUpdate: List<Server>, statusData: ByteArray): List<Server>?
}

private class BinaryStatusProcessingError(message: String) : Exception(message)

@Reusable
class UpdateServersWithBinaryStatusImpl @Inject constructor(
    private val prefs: ServerListUpdaterPrefs,
    private val userCountryIpBased: UserCountryIpBased,
) : UpdateServersWithBinaryStatus {

    @WorkerThread
    override operator fun invoke(serversToUpdate: List<Server>, statusData: ByteArray): List<Server>? {
        val uniffiLogicals = serversToUpdate.mapNotNull { server ->
            server.toUniffiLogical().also {
                if (it == null) {
                     logError("missing Server data for status computation ${server.toLog()}")
                }
            }
        }
        if (uniffiLogicals.size != serversToUpdate.size) {
            // This should not happen - all fields on LogicalServer are mandatory. The corresponding fields
            // on Server are nullable until we stop supporting v1.
            logAndReportToSentry("some servers have missing fields: ${serversToUpdate.size - uniffiLogicals.size}")
        }

        return {
            val loads = computeLoadsUniffi(
                logicals = uniffiLogicals,
                statusFile = statusData,
                userLocation = getLastKnownIpLocation(),
                userCountry = userCountryIpBased()?.countryCode,
            )
            if (loads.size == serversToUpdate.size) {
                serversToUpdate.zip(loads) { server, load ->
                    // Status update doesn't include physical servers, it's not safe to go from
                    // disabled to enabled without the full information.
                    val newIsOnline = load.isEnabled.takeIf { server.online } ?: false
                    server.copy(
                        isVisible = load.isVisible,
                        isOnline = newIsOnline,
                        load = load.load.toFloat(),
                        score = load.score
                    )
                }
            } else {
                logAndReportToSentry("incorrect number of results: server=${serversToUpdate.size} vs loads=${loads.size}")
                null
            }
        }.runCatchingCheckedExceptions { e ->
            logAndReportToSentry(e)
            null
        }
    }

    private fun Server.toUniffiLogical(): UniffiLogical? {
        if (statusReference == null || exitLocation == null || entryLocation == null) return null
        return UniffiLogical(
            with(statusReference) {
                UniffiStatusReference(index, penalty, cost.toUByte())
            },
            exitLocation = with(exitLocation) { UniffiLocation(latitude, longitude) },
            entryLocation = with(entryLocation) { UniffiLocation(latitude, longitude) },
            exitCountry = exitCountry,
            features = features.toUInt(),
        )
    }

    private fun getLastKnownIpLocation(): UniffiLocation? {
        val latitude = prefs.lastKnownIpLatitude
        val longitude = prefs.lastKnownIpLongitude
        return if (latitude != null && longitude != null) UniffiLocation(latitude, longitude) else null
    }

    private fun logAndReportToSentry(message: String) {
        Sentry.captureException(BinaryStatusProcessingError(message))
        logError(message)
    }

    private fun logAndReportToSentry(e: Throwable) {
        Sentry.captureException(e)
        logError("${e.message}\n${e.stacktraceMessage()}")
    }

    private fun logError(message: String) {
        ProtonLogger.logCustom(LogLevel.ERROR, LogCategory.APP, "Server status: $message")
    }
}
