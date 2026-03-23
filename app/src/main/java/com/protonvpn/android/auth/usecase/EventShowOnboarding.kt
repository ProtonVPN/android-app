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

package com.protonvpn.android.auth.usecase

import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.ui.planupgrade.IsInAppUpgradeAllowedUseCase
import com.protonvpn.android.utils.getValue
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import me.proton.core.auth.domain.feature.IsCredentialLessEnabled
import me.proton.core.plan.domain.usecase.GetDynamicSubscription
import javax.inject.Inject

sealed class OnboardingEvent {
    data object None: OnboardingEvent()
    data object ShowOnboarding: OnboardingEvent()
    data object ShowUpgradeOnboarding: OnboardingEvent()
    data class ShowUpgradeSuccess(val planName: String): OnboardingEvent()
}

interface EventShowOnboarding {
    val event: Flow<OnboardingEvent>
    fun onOnboardingShown()
}

@Reusable
class EventShowOnboardingImpl @Inject constructor(
    private val currentUser: CurrentUser,
    private val appFeaturesPrefs: AppFeaturesPrefs,
    private val isCredentialLessEnabled: IsCredentialLessEnabled,
    isInAppUpgradeAllowedUseCaseLazy: dagger.Lazy<IsInAppUpgradeAllowedUseCase>,
    getDynamicSubscriptionLazy: dagger.Lazy<GetDynamicSubscription>,
) : EventShowOnboarding {

    private val getDynamicSubscription by getDynamicSubscriptionLazy
    private val isInAppUpgradeAllowedUseCase by isInAppUpgradeAllowedUseCaseLazy

    override val event = combine(
        appFeaturesPrefs.showOnboardingUserIdFlow.distinctUntilChanged(),
        currentUser.vpnUserFlow.map { it?.userId }.distinctUntilChanged()
    ) { onboardingUserId, primaryUserId ->
        if (primaryUserId != null && primaryUserId.id == onboardingUserId) {
            primaryUserId
        } else {
            null
        }
    }.filterNotNull().map { userId ->
        val paidPlanName = getDynamicSubscription(userId)?.name
        when {
            paidPlanName != null -> OnboardingEvent.ShowUpgradeSuccess(paidPlanName)
            !isCredentialLessEnabled() -> OnboardingEvent.ShowOnboarding
            isInAppUpgradeAllowedUseCase() -> OnboardingEvent.ShowUpgradeOnboarding
            else -> OnboardingEvent.None
        }
    }.catch {
        emit(OnboardingEvent.None)
    }

    override fun onOnboardingShown() {
        appFeaturesPrefs.showOnboardingUserId = null
    }
}