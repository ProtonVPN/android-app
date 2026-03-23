/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.android.appconfig.usecase

import com.protonvpn.android.appconfig.UserCountryPhysical
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.di.ElapsedRealtimeClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.proton.core.featureflag.domain.ExperimentalProtonFeatureFlag
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

private val SKIP_PRIMARY_API_ROUTE_COUNTRIES = setOf("RU", "IR", "MM", "CN")
private val SKIP_PRIMARY_API_ROUTE_FOR_SELECTED_COUNTRIES_DISABLED_FEATURE_ID =
    FeatureId("SkipPrimaryApiRouteForSelectedCountriesDisabled")

@Singleton
class ShouldSkipPrimaryApiRoute @Inject constructor(
    private val userCountry: UserCountryPhysical,
    private val featureFlagManager: dagger.Lazy<FeatureFlagManager>,
    private val currentUser: CurrentUser,
    private val dispatcherProvider: VpnDispatcherProvider,
    @ElapsedRealtimeClock private val nowMs: () -> Long,
) {
    private var cachedValue: Boolean? = null
    private var cacheTimestamp: Long = 0L
    private val mutex = Mutex()

    suspend operator fun invoke(): Boolean {
        mutex.withLock {
            val now = nowMs()
            val validCached = cachedValue.takeIf { now - cacheTimestamp <= 10.minutes.inWholeMilliseconds }
            return validCached ?:
                shouldSkip().also {
                    cachedValue = it
                    cacheTimestamp = now
                }
        }
    }

    @OptIn(ExperimentalProtonFeatureFlag::class)
    private suspend fun shouldSkip(): Boolean =
        userCountry()?.countryCode in SKIP_PRIMARY_API_ROUTE_COUNTRIES &&
        // Use FeatureFlagManager.getValue to get cached value and avoid making API calls to
        // fetch the FF (this class is used during API call). We need to make a decision before
        // the first API call is made, but once feature flags are refreshed, we check for
        // kill-switch for this feature.
        withContext(dispatcherProvider.Io) {
            !featureFlagManager.get().getValue(
                currentUser.user()?.userId,
                SKIP_PRIMARY_API_ROUTE_FOR_SELECTED_COUNTRIES_DISABLED_FEATURE_ID
            )
        }
}
