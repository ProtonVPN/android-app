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
import me.proton.core.plan.domain.usecase.GetDynamicPlansAdjustedPrices
import me.proton.core.plan.presentation.entity.PlanCycle
import javax.inject.Inject

data class CycleInfo(
    val cycle: PlanCycle,
    val productId: String,
)
data class GiapPlanInfo(
    val dynamicPlan: DynamicPlan,
    val name: String,
    val displayName: String,
    val cycles: List<CycleInfo>,
    val preselectedCycle: PlanCycle,
)

@Reusable
class LoadGoogleSubscriptionPlans(
    private val vpnUserFlow: Flow<VpnUser?>,
    private val rawDynamicPlans: suspend (UserId?) -> List<DynamicPlan>,
    private val dynamicPlansAdjustedPrices: suspend (UserId?) -> List<DynamicPlan>,
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
        getDynamicPlansAdjustedPrices: GetDynamicPlansAdjustedPrices,
        getAvailablePaymentProviders: GetAvailablePaymentProviders
    ) : this(
        vpnUserFlow = currentUser.vpnUserFlow,
        rawDynamicPlans = { userId -> plansRepository.getDynamicPlans(userId, appStore).plans },
        dynamicPlansAdjustedPrices = { userId -> getDynamicPlansAdjustedPrices(userId).plans },
        availablePaymentProviders = getAvailablePaymentProviders::invoke,
        DEFAULT_CYCLES,
        DEFAULT_PRESELECTED_CYCLE
    )

    private fun DynamicPlan.getAvailableDefaultCycles() = defaultCycles.mapNotNull { cycle ->
        instances[cycle.cycleDurationMonths]?.vendors?.get(AppStore.GooglePlay)?.productId?.let {
            CycleInfo(cycle, it)
        }
    }

    private fun List<DynamicPlan>.filterAvailablePlans(planNames: List<String>) : List<DynamicPlan> =
        filter { plan -> plan.name in planNames && plan.state == DynamicPlanState.Available }
            .distinctBy { it.name }

    private fun DynamicPlan.toPlanInfo(): GiapPlanInfo? {
        val planName = name ?: return null
        val cycles = getAvailableDefaultCycles()

        if (cycles.isEmpty())
            return null
        val preselectedCycle = if (cycles.any { it.cycle == defaultPreselectedCycle })
            defaultPreselectedCycle
        else
            cycles.first().cycle
        return GiapPlanInfo(
            this,
            name = planName,
            displayName = title,
            cycles,
            preselectedCycle
        )
    }

    // Throws PartialPrices and whatever GetDynamicPlansAdjustedPrices and PlansRepository.getDynamicPlans may throw.
    suspend operator fun invoke(planNames: List<String>): List<GiapPlanInfo> {
        if (availablePaymentProviders().any { it != PaymentProvider.GoogleInAppPurchase })
            return emptyList()

        val vpnUser = vpnUserFlow.first() ?: return emptyList()
        if (vpnUser.hasSubscription)
            return emptyList()

        val availableCycleIds = rawDynamicPlans(vpnUser.userId)
            .filterAvailablePlans(planNames)
            .flatMap { plan ->
                plan.getAvailableDefaultCycles().map { cycle -> cycleId(plan.name ?: "unknown", cycle) }
            }
        val giapPlansWithPrices = dynamicPlansAdjustedPrices(vpnUser.userId)
            .filterAvailablePlans(planNames)
            .mapNotNull { it.toPlanInfo() }

        val giapPlansCycleIds = giapPlansWithPrices.flatMap { plan ->
            plan.cycles.map { cycle -> cycleId(plan.name, cycle) }
        }
        val missingPricesCycleIds =
            availableCycleIds.filterNot { expectedProductId -> giapPlansCycleIds.contains(expectedProductId) }
        if (missingPricesCycleIds.isNotEmpty() && missingPricesCycleIds.size < availableCycleIds.size) {
            throw PartialPrices(giapPlansCycleIds, missingPricesCycleIds)
        }

        return giapPlansWithPrices
    }

    private fun cycleId(planName: String, cycle: CycleInfo): String = "${planName}_${cycle.cycle.name}"

    companion object {
        // TODO: in future this should come from API
        private val DEFAULT_CYCLES = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY)
        private val DEFAULT_PRESELECTED_CYCLE = PlanCycle.YEARLY
    }
}
