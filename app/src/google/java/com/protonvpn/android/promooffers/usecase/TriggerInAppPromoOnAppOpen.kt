/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.promooffers.usecase

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.ElapsedRealtimeClock
import com.protonvpn.android.promooffers.data.ApiNotificationManager
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.utils.flatMapLatestIf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

private val UPDATE_INTERVAL_MS = 1.hours.inWholeMilliseconds

@Singleton
class TriggerInAppPromoOnAppOpen @Inject constructor(
    private val mainScope: CoroutineScope,
    private val apiNotificationManager: ApiNotificationManager,
    foregroundActivityTracker: ForegroundActivityTracker,
    private val isIapClientSidePromoCyclicEnabled: IsIapClientSidePromoCyclicEnabled,
    private val currentUser: CurrentUser,
    @param:ElapsedRealtimeClock private val clock: () -> Long,
) {
    private var lastUpdateTimestamp: Long = 0

    @OptIn(FlowPreview::class)
    private val triggerFlow = foregroundActivityTracker.isInForegroundFlow
        .onEach { inForeground ->
            // Updating the offers will regenerate them each time, let's not do this too often.
            if (inForeground && lastUpdateTimestamp + UPDATE_INTERVAL_MS < clock()) {
                lastUpdateTimestamp = clock()
                apiNotificationManager.updateIapIntroOffers(true)
            }
        }.cancellable()

    fun start() {
        combine(
            currentUser.vpnUserFlow,
            isIapClientSidePromoCyclicEnabled.observe(),
        ) { vpnUser, isEnabled ->
            vpnUser != null && isEnabled
        }.distinctUntilChanged()
        .flatMapLatestIf({ isEnabled -> isEnabled }) {
            triggerFlow
        }.launchIn(mainScope)
    }
}