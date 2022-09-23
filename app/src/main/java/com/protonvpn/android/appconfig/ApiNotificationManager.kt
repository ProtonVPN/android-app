/*
 * Copyright (c) 2020 Proton Technologies AG
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

package com.protonvpn.android.appconfig

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.bumptech.glide.Glide
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.promooffers.PromoOfferImage
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.jitterMs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.proton.core.util.kotlin.DispatcherProvider
import me.proton.core.util.kotlin.mapAsync
import me.proton.core.util.kotlin.mapNotNullAsync
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val MIN_NOTIFICATION_REFRESH_INTERVAL_MS = TimeUnit.HOURS.toMillis(3)

fun interface ImagePrefetcher {
    fun prefetch(url: String): Boolean
}

@Singleton
class GlideImagePrefetcher @Inject constructor(
    @ApplicationContext private val appContext: Context
) : ImagePrefetcher {
    override fun prefetch(url: String): Boolean {
        val future = Glide.with(appContext).download(url).submit()
        return try {
            future.get()
            true
        } catch (e: Throwable) {
            false
        }
    }
}

@Singleton
@SuppressWarnings("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
class ApiNotificationManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val mainScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @WallClock private val wallClockMs: () -> Long,
    private val appConfig: AppConfig,
    private val api: ProtonApiRetroFit,
    private val currentUser: CurrentUser,
    private val userPlanManager: UserPlanManager,
    private val appFeaturesPrefs: AppFeaturesPrefs,
    private val imagePrefetcher: ImagePrefetcher,
    foregroundActivityTracker: ForegroundActivityTracker
) {

    private val testNotifications = MutableStateFlow<List<ApiNotification>>(emptyList())

    private val apiNotificationsResponse = MutableStateFlow(
        Storage.load<ApiNotificationsResponse>(ApiNotificationsResponse::class.java) {
            ApiNotificationsResponse(emptyList())
        }
    )

    private val prefetchTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    private val allNotificationsFlow = apiNotificationsResponse
        .map { response -> response.notifications }
        .combine(testNotifications) { notifications, testNotifications ->
            testNotifications.ifEmpty { notifications }
        }

    private val notificationsFlow = allNotificationsFlow
        .combine(prefetchTrigger) { notifications, _ -> notifications }
        .mapLatest { notifications ->
            notifications.mapNotNullAsync { notification ->
                notification.takeIf { notification.allImageUrls().ensureAllPrefetched() }
            }
        }.flowOn(dispatcherProvider.Io)
        .stateIn(mainScope, SharingStarted.Eagerly, emptyList())

    val activeListFlow = notificationsFlow
        .onStart { prefetchTrigger.emit(Unit) }
        .flatMapLatest { notifications ->
            flow {
                var nextUpdateDelayS: Long? = 0
                while(nextUpdateDelayS != null) {
                    delay(TimeUnit.SECONDS.toMillis(nextUpdateDelayS))
                    val nowS = TimeUnit.MILLISECONDS.toSeconds(wallClockMs())
                    val activeNotifications = activeNotifications(nowS, notifications)
                    emit(activeNotifications)
                    nextUpdateDelayS = nextUpdateDelayS(nowS, notifications)
                }
            }
        }

    init {
        userPlanManager.infoChangeFlow
            .onEach {
                forceUpdate()
            }.launchIn(mainScope)
        appConfig.appConfigUpdateEvent
            .onEach {
                triggerUpdateIfNeeded()
            }.launchIn(mainScope)
        foregroundActivityTracker.foregroundActivityFlow
            .filterNotNull()
            .onEach {
                triggerUpdateIfNeeded()
            }.launchIn(mainScope)
    }

    @VisibleForTesting
    fun triggerUpdateIfNeeded() {
        mainScope.launch {
            updateIfNeeded()
        }
    }

    private suspend fun forceUpdate() {
        appFeaturesPrefs.minNextNotificationUpdateTimestamp = 0
        updateIfNeeded()
    }

    private suspend fun updateIfNeeded() {
        // isLoggedIn() is async so get the value before checking the time, otherwise two calls to updateIfNeeded in a
        // very short time can result in calling the API twice.
        val isLoggedIn = currentUser.isLoggedIn()
        val needsUpdate = wallClockMs() >= appFeaturesPrefs.minNextNotificationUpdateTimestamp
        if (needsUpdate && isLoggedIn) {
            appFeaturesPrefs.minNextNotificationUpdateTimestamp =
                wallClockMs() + jitterMs(MIN_NOTIFICATION_REFRESH_INTERVAL_MS)
            val response = if (appConfig.getFeatureFlags().pollApiNotifications) {
                val fullScreenImageSize = PromoOfferImage.getFullScreenImageMaxSizePx(appContext)
                api.getApiNotifications(
                    PromoOfferImage.SupportedFormats.values().map { it.toString() },
                    fullScreenImageSize.width,
                    fullScreenImageSize.height
                ).valueOrNull
            } else {
                ApiNotificationsResponse(emptyList())
            }
            response?.let {
                apiNotificationsResponse.value = response
                Storage.save(response)
            }
        }
    }

    private fun activeNotifications(nowS: Long, notifications: List<ApiNotification>) =
        notifications.filter { nowS >= it.startTime && nowS < it.endTime }

    private fun nextUpdateDelayS(nowS: Long, notifications: List<ApiNotification>): Long? =
        notifications.mapNotNull {
            when {
                nowS < it.startTime -> it.startTime - nowS
                nowS < it.endTime -> it.endTime - nowS
                else -> null
            }
        }.minOrNull()

    fun setTestNotificationsResponseJson(json: String) {
        try {
            testNotifications.value = Json.decodeFromString<ApiNotificationsResponse>(json).notifications
        } catch(e: Throwable) {
            Log.e("ApiNotificationManager", "Error parsing JSON", e)
        }
    }

    private fun ApiNotification.allImageUrls() = listOfNotNull(
        offer?.panel?.fullScreenImage?.let {
            PromoOfferImage.getFullScreenImageUrl(appContext, it)
        },
        offer?.panel?.pictureUrl,
        offer?.iconUrl
    ).filter { it.isNotBlank() }

    private suspend fun List<String>.ensureAllPrefetched(): Boolean = mapAsync { url ->
        imagePrefetcher.prefetch(url)
    }.all { isPrefetched -> isPrefetched }

}
