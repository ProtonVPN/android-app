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
import com.protonvpn.android.di.ElapsedRealtimeClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.proton.core.featureflag.domain.entity.FeatureId
import me.proton.core.featureflag.domain.repository.FeatureFlagRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

private val SKIP_PRIMARY_API_ROUTE_COUNTRIES = setOf("RU", "IR", "MM", "CN")
private val SKIP_PRIMARY_API_ROUTE_FOR_SELECTED_COUNTRIES_FEATURE_ID =
    FeatureId("SkipPrimaryApiRouteForSelectedCountries")

@Singleton
class ShouldSkipPrimaryApiRoute @Inject constructor(
    private val userCountry: UserCountryPhysical,
    private val featureFlagRepository: dagger.Lazy<FeatureFlagRepository>,
    private val currentUser: CurrentUser,
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

    private suspend fun shouldSkip(): Boolean =
        userCountry()?.countryCode in SKIP_PRIMARY_API_ROUTE_COUNTRIES &&
        // Use FeatureFlagRepository.getValue to get cached value and avoid making API calls to
        // fetch the FF (this class is used during API call). We assume default value to be true as
        // we need to make a decision before first API call is made.
        featureFlagRepository.get().getValue(
            currentUser.user()?.userId,
            SKIP_PRIMARY_API_ROUTE_FOR_SELECTED_COUNTRIES_FEATURE_ID
        ) != false
}
