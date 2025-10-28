/*
 * Copyright (c) 2020 Proton AG
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
import androidx.annotation.VisibleForTesting
import com.bumptech.glide.Glide
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.periodicupdates.IsInForeground
import com.protonvpn.android.appconfig.periodicupdates.IsLoggedIn
import com.protonvpn.android.appconfig.periodicupdates.PeriodicApiCallResult
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateSpec
import com.protonvpn.android.appconfig.periodicupdates.UpdateAction
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.ui.promooffers.PromoOfferImage
import com.protonvpn.android.ui.promooffers.usecase.GenerateNotificationsForIntroductoryOffers
import com.protonvpn.android.ui.promooffers.usecase.isIntroductoryPriceOffer
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.runCatchingCheckedExceptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiResult
import me.proton.core.util.kotlin.DispatcherProvider
import me.proton.core.util.kotlin.deserialize
import me.proton.core.util.kotlin.mapAsync
import me.proton.core.util.kotlin.mapNotNullAsync
import me.proton.core.util.kotlin.serialize
import me.proton.core.util.kotlin.takeIfNotEmpty
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
        return {
            future.get()
            true
        }.runCatchingCheckedExceptions {
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
    private val generateNotificationsForIntroductoryOffers: GenerateNotificationsForIntroductoryOffers,
    private val imagePrefetcher: ImagePrefetcher,
    private val periodicUpdateManager: PeriodicUpdateManager,
    @IsInForeground private val inForeground: Flow<Boolean>,
    @IsLoggedIn private val isLoggedIn: Flow<Boolean>,
) {

    private val testNotifications = MutableStateFlow<List<ApiNotification>>(emptyList())

    private val apiNotificationsResponse = MutableStateFlow(
        Storage.load<ApiNotificationsResponse>(ApiNotificationsResponse::class.java) {
            ApiNotificationsResponse(emptyList())
        }
    )

    private val prefetchTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    private val allNotificationsFlow = combine(
        apiNotificationsResponse.map { response -> response.notifications },
        testNotifications,
    ) { responseNotifications, testNotifications ->
        val notifications = testNotifications.ifEmpty { responseNotifications }
        logDebugNotifications(notifications)
        notifications.filter { notification ->
            notification.allActionNames().all { ApiNotificationActions.isSupported(it) }
        }.also {
            logDebugRemovedNotifications(notifications, it, "unsupported action")
        }
    }

    private val notificationsFlow = allNotificationsFlow
        .combine(prefetchTrigger) { notifications, _ -> notifications }
        .mapLatest { notifications ->
            notifications.mapNotNullAsync { notification ->
                notification.takeIf { notification.allImageUrls().ensureAllPrefetched() }
            }.also {
                logDebugRemovedNotifications(notifications, it, "can't load images")
            }
        }
        .distinctUntilChanged()
        .flowOn(dispatcherProvider.Io)
        .shareIn(mainScope, SharingStarted.Eagerly, replay = 1)

    // Active notifications are sorted by end time - the ones that end sooner are first.
    val activeListFlow = notificationsFlow
        .onStart { prefetchTrigger.emit(Unit) }
        .flatMapLatest { notifications ->
            flow {
                var nextUpdateDelayS: Long? = 0
                while (nextUpdateDelayS != null) {
                    delay(TimeUnit.SECONDS.toMillis(nextUpdateDelayS))
                    val nowS = TimeUnit.MILLISECONDS.toSeconds(wallClockMs())
                    val activeNotifications = activeNotifications(nowS, notifications)
                        .sortedBy { it.endTime }
                    emit(activeNotifications)
                    logDebugRemovedNotifications(notifications, activeNotifications, "time")
                    nextUpdateDelayS = nextUpdateDelayS(nowS, notifications)
                }
            }
        }

    private val notificationsUpdate =
        UpdateAction("in-app notifications") { PeriodicApiCallResult(updateNotifications()) }

    private var collectJob: Job? = null

    init {
        appConfig.appConfigFlow
            .map { it.featureFlags.pollApiNotifications }
            .distinctUntilChanged()
            .onEach { enabled -> if (enabled) enable() else disable() }
            .launchIn(mainScope)
    }

    private fun enable() {
        periodicUpdateManager.registerUpdateAction(
            notificationsUpdate,
            PeriodicUpdateSpec(MIN_NOTIFICATION_REFRESH_INTERVAL_MS, setOf(isLoggedIn, inForeground))
        )
        collectJob = merge(userPlanManager.infoChangeFlow, currentUser.eventVpnLogin)
            .onEach { forceUpdate() }
            .launchIn(mainScope)
    }

    private fun disable() {
        collectJob?.cancel()
        periodicUpdateManager.unregister(notificationsUpdate)
    }

    suspend fun forceUpdate() {
        periodicUpdateManager.executeNow(notificationsUpdate)
    }

    @VisibleForTesting
    suspend fun updateNotifications(): ApiResult<ApiNotificationsResponse> {
        val fullScreenImageSize = PromoOfferImage.getFullScreenImageMaxSizePx(appContext)
        val response = api.getApiNotifications(
            PromoOfferImage.SupportedFormats.entries.map { it.toString() },
            fullScreenImageSize.width,
            fullScreenImageSize.height
        )
        response.valueOrNull?.let { notifications ->
            replaceNotifications(notifications.notifications, iapIntroOffers = false)
        }

        mainScope.launch {
            // Checking intro offers may be a bit heavy, delay it to avoid running on start.
            delay(10_000)
            updateIapIntroOffers()
        }
        return response
    }

    suspend fun updateIapIntroOffers() {
        replaceNotifications(generateNotificationsForIntroductoryOffers(), iapIntroOffers = true)
    }

    private fun replaceNotifications(notifications: List<ApiNotification>, iapIntroOffers: Boolean) {
        val existingNotifications = apiNotificationsResponse.value.notifications
        val replacedNotifications =
            existingNotifications.filterNot { it.isIntroductoryPriceOffer() == iapIntroOffers } + notifications
        val data = ApiNotificationsResponse(replacedNotifications)
        apiNotificationsResponse.value = data
        Storage.save(data, ApiNotificationsResponse::class.java)
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
            testNotifications.value = json.deserialize<ApiNotificationsResponse>().notifications
        } catch(e: Throwable) {
            ProtonLogger.logCustom(LogLevel.ERROR, LogCategory.PROMO, "Error parsing JSON: $e")
        }
    }

    private fun ApiNotification.allImageUrls(): List<String> = buildList {
        val width = PromoOfferImage.getFullScreenImageMaxSizePx(appContext).width
        val fullScreenImages = listOfNotNull(
            offer?.panel?.fullScreenImage,
            offer?.panel?.button?.panel?.fullScreenImage
        )
        fullScreenImages.forEach { fullScreenImage ->
            add(PromoOfferImage.getFullScreenImageUrl(pixelWidth = width, isNightMode = true, fullScreenImage))
            add(PromoOfferImage.getFullScreenImageUrl(pixelWidth = width, isNightMode = false, fullScreenImage))
        }
        add(offer?.panel?.pictureUrl)
        add(offer?.iconUrl)
    }
    .filterNotNull()
    .filter(String::isNotBlank)

    private fun ApiNotification.allActionNames(): List<String> = buildList {
        val buttons: List<ApiNotificationOfferButton> = listOfNotNull(
            offer?.panel?.button,
            offer?.panel?.button?.panel?.button,
            offer?.prominentBanner?.actionButton,
        )
        return buttons.map { it.action }
    }

    private suspend fun List<String>.ensureAllPrefetched(): Boolean = mapAsync { url ->
        imagePrefetcher.prefetch(url)
    }.all { isPrefetched -> isPrefetched }

    private fun logDebugNotifications(notifications: Iterable<ApiNotification>) {
        if (BuildConfig.DEBUG) {
            notifications.forEach {
                val jsonString = it.serialize()
                ProtonLogger.logCustom(LogLevel.DEBUG, LogCategory.PROMO, "Parsed JSON:\n$jsonString")
            }
        }
    }

    private fun logDebugRemovedNotifications(
        before: Iterable<ApiNotification>,
        after: Iterable<ApiNotification>,
        reason: String
    ) {
        if (BuildConfig.DEBUG) {
            val removedIds = before.toSet().subtract(after).takeIfNotEmpty()?.joinToString(", ") { it.id }
            if (removedIds != null) {
                ProtonLogger.logCustom(
                    LogLevel.DEBUG,
                    LogCategory.PROMO,
                    "Ignored notifications ($reason): $removedIds"
                )
            }
        }
    }
}
