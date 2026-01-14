/*
 * Copyright (c) 2022 Proton AG
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
package com.protonvpn.android.ui.onboarding

import android.app.Activity
import androidx.annotation.VisibleForTesting
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class ReviewTracker constructor(
    @WallClock private val wallClock: () -> Long,
    scope: CoroutineScope,
    private val appConfig: AppConfig,
    private val currentUser: CurrentUser,
    vpnMonitor: VpnStateMonitor,
    private val foregroundActivityTracker: ForegroundActivityTracker,
    private val reviewTrackerPrefs: ReviewTrackerPrefs,
    trafficMonitor: TrafficMonitor,
    private val telemetry: ReviewTrackerTelemetry,
    private val requestReview: suspend (activity: Activity, onComplete: () -> Unit) -> Unit
) {

    @Inject
    constructor(
        @WallClock wallClock: () -> Long,
        scope: CoroutineScope,
        appConfig: AppConfig,
        currentUser: CurrentUser,
        vpnMonitor: VpnStateMonitor,
        foregroundActivityTracker: ForegroundActivityTracker,
        reviewTrackerPrefs: ReviewTrackerPrefs,
        trafficMonitor: TrafficMonitor,
        telemetry: ReviewTrackerTelemetry,
    ) : this(
        wallClock,
        scope,
        appConfig,
        currentUser,
        vpnMonitor,
        foregroundActivityTracker,
        reviewTrackerPrefs,
        trafficMonitor,
        telemetry,
        ::requestInAppReview
    )

    init {
        vpnMonitor.vpnConnectionNotificationFlow.onEach {
            // Reset successful connections on ANY fallbacks. Even on ones which we handle gracefully
            reviewTrackerPrefs.successConnectionsInRow = 0
        }.launchIn(scope)

        trafficMonitor.trafficStatus.observeForever {
            it?.let {
                val sessionTimeInMillis = TimeUnit.SECONDS.toMillis(it.sessionTimeSeconds.toLong())
                val toBeEligableInMillis =
                    TimeUnit.DAYS.toMillis(appConfig.getRatingConfig().daysConnectedCount.toLong())
                if (sessionTimeInMillis > toBeEligableInMillis) {
                    reviewTrackerPrefs.longSessionReached = true
                    scope.launch {
                        if (shouldRate()) createInAppReview()
                    }
                }
            }
        }

        vpnMonitor.status.onEach {
            if (it.state == VpnState.Connected) {
                if (reviewTrackerPrefs.firstConnectionTimestamp == 0L)
                    reviewTrackerPrefs.firstConnectionTimestamp = wallClock()

                reviewTrackerPrefs.successConnectionsInRow++
                reviewTrackerPrefs.connectionsSinceLastReview++
                if (shouldRate()) {
                    createInAppReview()
                }
            }
        }.launchIn(scope)
    }

    private suspend fun createInAppReview() {
        foregroundActivityTracker.foregroundActivity?.let {
            requestReview(it) {
                telemetry.reportReviewRequest(
                    lastReviewTimestamp = reviewTrackerPrefs.lastReviewTimestamp,
                    installTimestamp = reviewTrackerPrefs.installTimestamp,
                    connectionsSinceLastReview = reviewTrackerPrefs.connectionsSinceLastReview
                )
                reviewTrackerPrefs.lastReviewTimestamp = wallClock()
                reviewTrackerPrefs.longSessionReached = false
                reviewTrackerPrefs.connectionsSinceLastReview = 0
                log("Review flow was triggered " + reviewTrackerPrefs.lastReviewTimestamp)
            }
        }
    }

    suspend fun isOrWasEligibleToday(): Boolean? {
        val wasEligibleToday = (wallClock() - reviewTrackerPrefs.lastReviewTimestamp).milliseconds.inWholeDays == 0L
        return isEligibleForReviewNow() || wasEligibleToday
    }

    @VisibleForTesting
    fun connectionCount(): Int = reviewTrackerPrefs.successConnectionsInRow

    private fun getWithDefaultMaxValue(value: Long): Long {
        return if (value == 0L)
            Long.MAX_VALUE
        else
            TimeUnit.MILLISECONDS.toDays(wallClock() - value)
    }

    @VisibleForTesting
    suspend fun shouldRate(): Boolean {
        if (!isEligibleForReviewNow()) return false
        if (foregroundActivityTracker.foregroundActivity == null) return false

        val ratingConfig = appConfig.getRatingConfig()
        log("Connections in queue: " + (reviewTrackerPrefs.successConnectionsInRow >= ratingConfig.successfulConnectionCount))
        log("Long session reached: " + (reviewTrackerPrefs.longSessionReached))
        log("---------")

        return (reviewTrackerPrefs.successConnectionsInRow >= ratingConfig.successfulConnectionCount ||
            reviewTrackerPrefs.longSessionReached)
    }

    private suspend fun isEligibleForReviewNow(): Boolean {
        val ratingConfig = appConfig.getRatingConfig()

        val isPlanEligible = ratingConfig.eligiblePlans.contains(currentUser.vpnUser()?.planName)
        log("User plan eligable for review suggestion: $isPlanEligible")
        if (!isPlanEligible) return false

        val firstConnectionDaysAgo = getWithDefaultMaxValue(reviewTrackerPrefs.firstConnectionTimestamp)
        val lastReviewDaysAgo = getWithDefaultMaxValue(reviewTrackerPrefs.lastReviewTimestamp)
        log("First connection attempt days ago: $firstConnectionDaysAgo")
        // Do not trigger if first connection attempt was recent
        if (ratingConfig.daysFromFirstConnectionCount > firstConnectionDaysAgo) return false
        log("Last review days ago: $lastReviewDaysAgo")
        // Do not trigger in-app review if it was called recently
        if (ratingConfig.daysSinceLastRatingCount > lastReviewDaysAgo) return false

        return (reviewTrackerPrefs.successConnectionsInRow >= ratingConfig.successfulConnectionCount ||
                reviewTrackerPrefs.longSessionReached)
    }

    companion object {
        private suspend fun requestInAppReview(activity: Activity, onComplete: () -> Unit) {
            log("Suggest in app review")
            try {
                val manager = ReviewManagerFactory.create(activity)
                val reviewInfo = manager.requestReview()
                manager
                    .launchReviewFlow(activity, reviewInfo)
                    .addOnCompleteListener { onComplete() }
            } catch (e: Exception) {
                log("Failure to contact google play: ${e.message}")
            }
        }

        private fun log(message: String) {
            ProtonLogger.logCustom(
                LogLevel.DEBUG,
                LogCategory.APP_REVIEW,
                message
            )
        }
    }
}
