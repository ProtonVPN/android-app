/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.ui.planupgrade.usecase

import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.ui.planupgrade.IapConstants
import com.protonvpn.android.ui.planupgrade.LoadGoogleOffers
import com.protonvpn.android.ui.planupgrade.getSingleCurrency
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.proton.core.domain.entity.AppStore
import me.proton.core.domain.entity.UserId
import me.proton.core.payment.domain.usecase.GetAvailablePaymentProviders
import me.proton.core.payment.domain.usecase.PaymentProvider
import me.proton.core.plan.domain.entity.DynamicPlan
import me.proton.core.plan.domain.entity.DynamicPlanState
import me.proton.core.plan.domain.repository.PlansRepository
import me.proton.core.plan.presentation.entity.PlanCycle
import javax.inject.Inject

data class CycleInfo(
    val cycle: PlanCycle,
    val productId: String,
    val offerToken: String,
    val currentPriceCents: Int,
    val defaultPriceCents: Int,
)

data class GiapOfferInfo(
    val dynamicPlan: DynamicPlan,
    val currency: String,
    val cycle: CycleInfo,
    val offerTags: List<String>,
)

data class GiapPlanInfo(
    val dynamicPlan: DynamicPlan,
    val name: String,
    val displayName: String,
    val currency: String,
    val cycles: List<CycleInfo>,
    val preselectedCycle: PlanCycle,
)

@Reusable
class LoadGoogleSubscriptionPlans(
    private val vpnUserFlow: Flow<VpnUser?>,
    private val rawDynamicPlans: suspend (UserId?) -> List<DynamicPlan>,
    private val loadGoogleOffers: suspend (List<DynamicPlan>) -> List<GiapOfferInfo>,
    private val availablePaymentProviders: suspend () -> Set<PaymentProvider>,
    private val defaultCycles: List<PlanCycle>,
    private val defaultPreselectedCycle: PlanCycle
) {
    class PartialPrices(
        loadedPricesCycleIds: List<String>,
        missingPricesCycleIds: List<String>
    ) : Exception(
        "Google prices available only for a subset of plans/cycles: $loadedPricesCycleIds; " +
            "missing for: $missingPricesCycleIds"
    )

    @Inject constructor(
        currentUser: CurrentUser,
        appStore: AppStore,
        plansRepository: PlansRepository,
        loadGoogleOffers: LoadGoogleOffers,
        getAvailablePaymentProviders: GetAvailablePaymentProviders
    ) : this(
        vpnUserFlow = currentUser.vpnUserFlow,
        rawDynamicPlans = { userId -> plansRepository.getDynamicPlans(userId, appStore).plans },
        loadGoogleOffers = loadGoogleOffers::invoke,
        availablePaymentProviders = getAvailablePaymentProviders::invoke,
        DEFAULT_CYCLES,
        DEFAULT_PRESELECTED_CYCLE
    )
    private fun List<DynamicPlan>.filterAvailablePlans(planNames: List<String>) : List<DynamicPlan> =
        filter { plan -> plan.name in planNames && plan.state == DynamicPlanState.Available }
            .distinctBy { it.name }

    suspend operator fun invoke(
        offerTag: String,
        planNames: List<String>,
        fallbackOfferTag: String = IapConstants.BASE_PRICE_TAG,
    ): List<GiapPlanInfo> {
        if (availablePaymentProviders().any { it != PaymentProvider.GoogleInAppPurchase })
            return emptyList()

        val vpnUser = vpnUserFlow.first() ?: return emptyList()
        if (vpnUser.hasSubscription) {
            ProtonLogger.logCustom(LogCategory.USER, "GIAP unavailable, user has a subscription")
            return emptyList()
        }

        val availableDynamicPlans = rawDynamicPlans(vpnUser.userId)
            .filterAvailablePlans(planNames)
        val availableCycleIds = availableDynamicPlans
            .flatMap { plan ->
                val currency = plan.getSingleCurrency() ?: return@flatMap emptyList()
                defaultCycles.mapNotNull { cycle ->
                    val planInstanceForCycle = plan.instances[cycle.cycleDurationMonths]
                    val price = planInstanceForCycle?.price[currency]
                    val productId = planInstanceForCycle?.vendors?.get(AppStore.GooglePlay)?.productId
                    if (price != null && productId != null) {
                        cycleId(plan.name ?: "unknown", cycle)
                    } else {
                        null
                    }
                }
            }

        val giapPlans = loadGoogleOffers(availableDynamicPlans)
            .filter { it.cycle.cycle in defaultCycles }
            .groupBy { it.dynamicPlan }
            .mapNotNull { (plan, allOffers) ->
                val name = plan.name ?: return@mapNotNull null
                val offersByCycle = allOffers.groupBy { it.cycle.cycle }
                val offers = offersByCycle.mapNotNull { (_, offers) ->
                    offers.find { it.offerTags.contains(offerTag) }
                        ?: offers.find { it.offerTags.contains(fallbackOfferTag) }
                }
                val cycles = offers.map { it.cycle }
                if (cycles.isEmpty()) {
                    ProtonLogger.logCustom(LogCategory.USER,"GIAP: plan '$name' has no cycles.")
                    return@mapNotNull null
                }

                val firstCurrency = offers.first().currency
                if (!offers.all { it.currency == firstCurrency}) {
                    ProtonLogger.logCustom(LogCategory.USER,"GIAP: plan '$name' has multiple currencies.")
                    return@mapNotNull null
                }

                val preselectedCycle = if (cycles.any { it.cycle == defaultPreselectedCycle }) {
                    defaultPreselectedCycle
                } else {
                    cycles.first().cycle
                }
                GiapPlanInfo(
                    dynamicPlan = plan,
                    name = name,
                    displayName = plan.title,
                    currency = offers.first().currency,
                    cycles = cycles,
                    preselectedCycle = preselectedCycle
                )
            }

        val giapPlansCycleIds = giapPlans.flatMap { plan ->
            plan.cycles.map { cycle -> cycleId(plan.name, cycle.cycle) }
        }
        val missingPricesCycleIds =
            availableCycleIds.filterNot { expectedProductId -> giapPlansCycleIds.contains(expectedProductId) }
        if (missingPricesCycleIds.isNotEmpty() && missingPricesCycleIds.size < availableCycleIds.size) {
            throw PartialPrices(giapPlansCycleIds, missingPricesCycleIds)
        }

        return giapPlans
    }

    private fun cycleId(planName: String, cycle: PlanCycle): String = "${planName}_${cycle.name}"

    companion object {
        // TODO: in future this should come from API
        private val DEFAULT_CYCLES = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY)
        private val DEFAULT_PRESELECTED_CYCLE = PlanCycle.YEARLY
    }
}