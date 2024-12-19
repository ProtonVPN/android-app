/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.telemetry

import com.protonvpn.android.BuildConfig
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import dagger.Reusable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private val DISCARD_AGE = 7.days
private const val MAX_EVENT_COUNT = 100

@Serializable
data class TelemetryEvent(
    val timestamp: Long,
    val measurementGroup: String,
    val event: String,
    val values: Map<String, Long>,
    val dimensions: Map<String, String>
)

class TelemetryError(message: String, cause: Throwable? = null) : Throwable(message, cause)

interface TelemetryUploadScheduler {
    fun scheduleTelemetryUpload()
    fun scheduleImmediateTelemetryUpload()
}
interface SnapshotScheduler {
    fun scheduleSettingsSnapshot()
}

@Reusable
class NoopTelemetryUploadScheduler @Inject constructor() : TelemetryUploadScheduler {
    override fun scheduleTelemetryUpload() = Unit
    override fun scheduleImmediateTelemetryUpload() = Unit
}
@Reusable
class NoopSnapshotScheduler @Inject constructor() : SnapshotScheduler {
    override fun scheduleSettingsSnapshot() = Unit
}

@Singleton
class Telemetry(
    private val mainScope: CoroutineScope,
    @WallClock private val wallClock: () -> Long,
    private val userSettings: EffectiveCurrentUserSettings,
    private val cache: TelemetryCache,
    private val uploader: TelemetryUploader,
    private val uploadScheduler: TelemetryUploadScheduler,
    snapshotScheduler: SnapshotScheduler,
    private val discardAge: Duration,
    private val eventCountLimit: Int,
) {

    private val pendingEvents: MutableList<TelemetryEvent> = mutableListOf()
    private val cacheLoaded = CompletableDeferred<Unit>()
    private var isUploading = false

    sealed class UploadResult {
        data class Success(val hasMoreEvents: Boolean) : UploadResult()
        data class Failure(val isRetryable: Boolean, val retryAfter: Duration?) : UploadResult()
    }

    init {
        mainScope.launch {
            loadCache()
        }
        snapshotScheduler.scheduleSettingsSnapshot()
    }

    @Inject constructor(
        mainScope: CoroutineScope,
        @WallClock wallClock: () -> Long,
        userSettings: EffectiveCurrentUserSettings,
        cache: TelemetryCache,
        uploader: TelemetryUploader,
        uploadScheduler: TelemetryUploadScheduler,
        snapshotScheduler: SnapshotScheduler,
    ) : this(
        mainScope, wallClock, userSettings, cache, uploader, uploadScheduler, snapshotScheduler, DISCARD_AGE, MAX_EVENT_COUNT
    )

    fun event(
        measurementGroup: String,
        event: String,
        values: Map<String, Long>,
        dimensions: Map<String, String>,
        sendImmediately: Boolean = false
    ) {
        mainScope.launch {
            if (isEnabled()) {
                logd("$measurementGroup $event: $values $dimensions")
                addEvent(TelemetryEvent(wallClock(), measurementGroup, event, values, dimensions), sendImmediately)
            }
        }
    }

    suspend fun uploadPendingEvents(): UploadResult {
        cacheLoaded.await()
        if (!isEnabled()) {
            clearData()
            return UploadResult.Success(false)
        }
        if (isUploading) {
            logi("Trying to start a second upload in parallel, this should not happen.")
            return UploadResult.Success(false)
        }
        try {
            isUploading = true
            return uploadPendingEventsInternal()
        } finally {
            isUploading = false
        }
    }

    private suspend fun uploadPendingEventsInternal(): UploadResult {
        pendingEvents.removeIf { it.timestamp < wallClock() - discardAge.inWholeMilliseconds }
        val toUpload = pendingEvents.toList() // Work on a copy, new events may be added to pendingEvents during upload.
        val result = if (toUpload.isNotEmpty()) {
            uploader.uploadEvents(toUpload)
        } else {
            // Remove once VPNAND-1221 is fixed.
            // Disabled, see the Jira issue.
            //Sentry.captureException(TelemetryError("No events to upload"))
            UploadResult.Success(pendingEvents.isNotEmpty())
        }
        return if (result is UploadResult.Success) {
            pendingEvents.removeAll(toUpload)
            cache.save(pendingEvents)
            UploadResult.Success(pendingEvents.isNotEmpty())
        } else {
            result
        }
    }

    private suspend fun addEvent(event: TelemetryEvent, sendImmediately: Boolean) {
        cacheLoaded.await()
        val isFirstEvent = pendingEvents.isEmpty()
        pendingEvents.add(event)
        if (pendingEvents.size > eventCountLimit) {
            pendingEvents.removeAt(0)
        }
        logi("event added, total: ${pendingEvents.size}")
        cache.save(pendingEvents)
        when {
            sendImmediately -> uploadScheduler.scheduleImmediateTelemetryUpload()
            isFirstEvent -> uploadScheduler.scheduleTelemetryUpload()
        }
    }

    private suspend fun isEnabled() = userSettings.telemetry.first()

    private fun logi(message: String) {
        // Remove once VPNAND-1221 is fixed.
        ProtonLogger.logCustom(LogCategory.TELEMETRY, message)
    }

    private fun logd(message: String) {
        if (BuildConfig.DEBUG || BuildConfig.ALLOW_LOGCAT) {
            ProtonLogger.logCustom(LogLevel.DEBUG, LogCategory.TELEMETRY, message)
        }
    }

    private suspend fun loadCache() {
        val cachedEvents = cache.load(wallClock() - discardAge.inWholeMilliseconds).run {
            subList(maxOf(0, size + pendingEvents.size - eventCountLimit), size)
        }
        pendingEvents.addAll(0, cachedEvents)
        logi("Cache loaded. Cached events: ${cachedEvents.size}, total: ${pendingEvents.size}")
        cacheLoaded.complete(Unit)
    }

    private fun clearData() {
        pendingEvents.clear()
        cache.save(emptyList())
    }
}
