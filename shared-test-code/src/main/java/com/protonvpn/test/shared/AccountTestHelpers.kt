/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.test.shared

import me.proton.core.domain.entity.AppStore
import me.proton.core.domain.entity.UserId
import me.proton.core.plan.domain.entity.DynamicPlan
import me.proton.core.plan.domain.entity.DynamicPlanInstance
import me.proton.core.plan.domain.entity.DynamicPlanPrice
import me.proton.core.plan.domain.entity.DynamicPlanState
import me.proton.core.plan.domain.entity.DynamicPlanVendor
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.user.domain.entity.Type
import me.proton.core.user.domain.entity.User
import java.time.Instant

// We should upstream such helpers to Account modules.
fun createAccountUser(id: UserId = UserId("id"), type: Type = Type.Proton, createdAtUtc: Long = 0L, name: String? = null) = User(
    userId = id,
    email = null,
    name = name,
    displayName = null,
    currency = "EUR",
    type = type,
    credit = 0,
    createdAtUtc = createdAtUtc,
    usedSpace = 0,
    maxSpace = 0,
    maxUpload = 0,
    role = null,
    private = false,
    subscribed = 0,
    services = 0,
    delinquent = null,
    recovery = null,
    keys = emptyList(),
    flags = emptyMap()
)

fun PlanCycle.toProductId(appStore: AppStore) = "productId-$appStore-$cycleDurationMonths"

fun createDynamicPlan(
    name: String,
    prices: Map<PlanCycle, Map</*currency*/String, DynamicPlanPrice>> = emptyMap(),
    appStore: AppStore = AppStore.GooglePlay
) = createDynamicPlan(
    name = name,
    instances = prices.map { (cycle, cyclePrices) ->
        cycle.cycleDurationMonths to createDynamicPlanInstance(cycle, appStore, cyclePrices)
    }.toMap()
)

fun createDynamicPlan(
    name: String,
    instances: Map<Int, DynamicPlanInstance>,
) = DynamicPlan(
    name,
    0,
    DynamicPlanState.Available,
    "$name title",
    null,
    instances = instances
)

fun createDynamicPlanInstance(
    cycle: PlanCycle,
    appStore: AppStore,
    currencyToPrice: Map<String, DynamicPlanPrice>?
) = DynamicPlanInstance(
    cycle.cycleDurationMonths, "", Instant.MAX, currencyToPrice ?: emptyMap(),
    mapOf(appStore to DynamicPlanVendor(cycle.toProductId(appStore), ""))
)
