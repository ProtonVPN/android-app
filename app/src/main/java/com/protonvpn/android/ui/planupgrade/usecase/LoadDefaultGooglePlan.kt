/*
 * Copyright (c) 2023. Proton Technologies AG
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

import android.content.res.Resources
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
import me.proton.core.plan.domain.usecase.GetDynamicPlans
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.plan.presentation.entity.getSelectedPlan
import javax.inject.Inject

data class GiapPlanInfo(
    private val dynamicPlan: DynamicPlan,
    val name: String,
    val displayName: String,
    val cycle: PlanCycle,
    val productId: String
) {
    fun getSelectedPlan(resources: Resources, cycleDurationMonths: Int) =
        dynamicPlan.getSelectedPlan(resources, cycleDurationMonths, null)
}

@Reusable
class LoadDefaultGooglePlan(
    private val vpnUserFlow: Flow<VpnUser?>,
    private val dynamicPlans: suspend (UserId?) -> List<DynamicPlan>,
    private val availablePaymentProviders: suspend () -> Set<PaymentProvider>,
    private val defaultCycle: PlanCycle,
) {

    @Inject constructor(
        currentUser: CurrentUser,
        getDynamicPlans: GetDynamicPlans,
        getAvailablePaymentProviders: GetAvailablePaymentProviders
    ) : this(
        vpnUserFlow = currentUser.vpnUserFlow,
        dynamicPlans = { getDynamicPlans(it).plans },
        availablePaymentProviders = getAvailablePaymentProviders::invoke,
        DEFAULT_CYCLE
    )

    private suspend fun defaultDynamicPlan() : DynamicPlan? {
        val vpnUser = vpnUserFlow.first() ?: return null
        if (vpnUser.hasSubscription)
            return null
        return dynamicPlans(vpnUser.userId).firstOrNull {
            it.name == DEFAULT_PLAN_NAME_VPN && it.state == DynamicPlanState.Available
        }
    }

    private fun DynamicPlan.toPlanInfo(): GiapPlanInfo? {
        val cycle =
            defaultCycle.takeIf { defaultCycle.cycleDurationMonths in instances.keys } ?:
            PlanCycle.values().firstOrNull {
                it.cycleDurationMonths in instances.keys && it.cycleDurationMonths > 0
            } ?: return null
        val instance = instances[cycle.cycleDurationMonths] ?: return null
        val planName = name ?: return null
        val productId = instance.vendors[AppStore.GooglePlay]?.productId ?: return null
        return GiapPlanInfo(this, name = planName, displayName = title, cycle, productId)
    }

    suspend operator fun invoke(): GiapPlanInfo? {
        if (availablePaymentProviders().any { it != PaymentProvider.GoogleInAppPurchase })
            return null

        return defaultDynamicPlan()?.toPlanInfo()
    }

    companion object {
        // TODO: in future this should come from API
        private val DEFAULT_CYCLE = PlanCycle.MONTHLY
        const val DEFAULT_PLAN_NAME_VPN = "vpn2022"
    }
}
