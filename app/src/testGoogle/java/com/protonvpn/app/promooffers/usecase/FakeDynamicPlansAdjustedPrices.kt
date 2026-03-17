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

package com.protonvpn.app.promooffers.usecase

import me.proton.core.domain.entity.UserId
import me.proton.core.plan.domain.entity.DynamicPlan
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.util.kotlin.equalsNoCase

class FakeDynamicPlansAdjustedPrices(private val rawDynamicPlans: () -> List<DynamicPlan>) {

    lateinit var currency: String
    var introPrices: Map<PlanCycle, Int> = emptyMap()

    var wasCalled = false
        private set

    fun resetWasCalled() {
        wasCalled = false
    }

    fun invoke(userId: UserId?): List<DynamicPlan> {
        wasCalled = true
        return rawDynamicPlans().map { plan ->
            plan.copy(instances = plan.instances.mapValues { (cycleMonths, instance) ->
                val planCycle = PlanCycle.entries.find { it.cycleDurationMonths == cycleMonths }!!
                val prices = instance.price
                    .filterKeys { it equalsNoCase currency }
                    .mapValues { (_, price) ->
                        val introPrice = introPrices[planCycle]
                        if (introPrice != null) {
                            price.copy(default = price.current, current = introPrice)
                        } else {
                            price
                        }
                    }
                instance.copy(price = prices)
            })
        }
    }
}