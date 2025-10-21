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

package com.protonvpn.android.ui.promooffers.usecase

import com.protonvpn.android.ui.planupgrade.IsInAppUpgradeAllowedUseCase
import com.protonvpn.android.ui.planupgrade.getSingleCurrency
import com.protonvpn.android.ui.planupgrade.usecase.LoadGoogleSubscriptionPlans
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.runCatchingCheckedExceptions
import dagger.Reusable
import io.sentry.Sentry
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.ApiResult
import me.proton.core.plan.presentation.entity.PlanCycle
import javax.inject.Inject

@Reusable
class GetEligibleIntroductoryOffers @Inject constructor(
    private val loadGoogleSubscriptionPlans: LoadGoogleSubscriptionPlans,
    private val inAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase,
) {
    data class Offer(
        val planName: String,
        val cycle: PlanCycle,
        val currency: String,
        val introPriceCents: Int
    )

    suspend operator fun invoke(planNames: List<String>): List<Offer>? {
        if (!inAppUpgradeAllowed()) return null

        return suspend {
            val giapPlans = loadGoogleSubscriptionPlans(planNames)

            val introOffers = giapPlans.flatMap { plan ->
                val currency = plan.dynamicPlan.getSingleCurrency() ?: return@flatMap emptyList()

                plan.cycles.mapNotNull { cycle ->
                    val planInstance = plan.dynamicPlan.instances[cycle.cycle.cycleDurationMonths]
                    val price = planInstance?.price?.get(currency)
                    val currentPriceCents = price?.current
                    val renewPriceCents = price?.default

                    if (currentPriceCents != null && renewPriceCents != null && currentPriceCents < renewPriceCents) {
                        Offer(
                            planName = plan.name,
                            cycle = cycle.cycle,
                            currency = currency,
                            introPriceCents = currentPriceCents
                        )
                    } else {
                        null
                    }
                }
            }
            introOffers
        }.runCatchingCheckedExceptions { e ->
            if (shouldReportToSentry(e))
                Sentry.captureException(GetIntroPricesError("Error fetching intro prices", e))
            null
        }
    }

    private fun shouldReportToSentry(throwable: Throwable?): Boolean =
        throwable == null || (throwable as? ApiException)?.error !is ApiResult.Error.Connection
}

private class GetIntroPricesError(message: String, cause: Throwable) : Exception(message, cause)
