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
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.UserData
import dagger.Reusable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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

interface TelemetryUploadScheduler {
    fun scheduleTelemetryUpload()
}

@Reusable
class NoopTelemetryUploadScheduler @Inject constructor() : TelemetryUploadScheduler {
    override fun scheduleTelemetryUpload() = Unit
}

@Singleton
class Telemetry(
    mainScope: CoroutineScope,
    @WallClock private val wallClock: () -> Long,
    private val appConfig: AppConfig,
    private val userData: UserData,
    private val cache: TelemetryCache,
    private val uploader: TelemetryUploader,
    private val uploadScheduler: TelemetryUploadScheduler,
    private val discardAge: Duration,
    private val eventCountLimit: Int,
) {

    private val pendingEvents: MutableList<TelemetryEvent> = mutableListOf()

    private val cacheLoaded = CompletableDeferred<Unit>()

    sealed class UploadResult {
        data class Success(val hasMoreEvents: Boolean) : UploadResult()
        data class Failure(val retryAfter: Duration?) : UploadResult()
    }

    private val isEnabled: Boolean get() = appConfig.getFeatureFlags().telemetry && userData.telemetryEnabled

    init {
        mainScope.launch {
            loadCache()
        }
    }

    @Inject constructor(
        mainScope: CoroutineScope,
        @WallClock wallClock: () -> Long,
        appConfig: AppConfig,
        userData: UserData,
        cache: TelemetryCache,
        uploader: TelemetryUploader,
        uploadScheduler: TelemetryUploadScheduler,
    ) : this(
        mainScope, wallClock, appConfig, userData, cache, uploader, uploadScheduler, DISCARD_AGE, MAX_EVENT_COUNT
    )

    fun event(
        measurementGroup: String,
        event: String,
        values: Map<String, Long>,
        dimensions: Map<String, String>
    ) {
        if (isEnabled) {
            log("$measurementGroup $event: $values $dimensions")
            addEvent(TelemetryEvent(wallClock(), measurementGroup, event, values, dimensions))
        }
    }

    suspend fun uploadPendingEvents(): UploadResult {
        cacheLoaded.await()
        if (!isEnabled) {
            clearData()
            return UploadResult.Success(false)
        }
        pendingEvents.removeIf { it.timestamp < wallClock() - discardAge.inWholeMilliseconds }
        val toUpload = pendingEvents.toList() // Work on a copy, new events may be added to pendingEvents during upload.
        val result = uploader.uploadEvents(toUpload)
        return if (result is UploadResult.Success) {
            pendingEvents.removeAll(toUpload)
            cache.save(pendingEvents)
            UploadResult.Success(pendingEvents.isNotEmpty())
        } else {
            result
        }
    }

    private fun addEvent(event: TelemetryEvent) {
        uploadScheduler.scheduleTelemetryUpload()
        pendingEvents.add(event)
        if (pendingEvents.size > eventCountLimit)
            pendingEvents.removeFirst()
        cache.save(pendingEvents)
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) {
            ProtonLogger.logCustom(LogLevel.DEBUG, LogCategory.TELEMETRY, message)
        }
    }

    private suspend fun loadCache() {
        val cachedEvents = cache.load(wallClock() - discardAge.inWholeMilliseconds).run {
            subList(maxOf(0, size + pendingEvents.size - eventCountLimit), size)
        }
        pendingEvents.addAll(0, cachedEvents)
        cacheLoaded.complete(Unit)
    }

    private fun clearData() {
        pendingEvents.clear()
        cache.save(emptyList())
    }
}
