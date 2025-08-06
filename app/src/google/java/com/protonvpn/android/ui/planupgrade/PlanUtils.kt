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

package com.protonvpn.android.ui.planupgrade

import me.proton.core.plan.domain.entity.DynamicPlan
import kotlin.collections.component1
import kotlin.collections.component2

/// Returns null if there is more than one currency or if the plan is empty
fun DynamicPlan.getSingleCurrency(): String? {
    val currencies = instances
        .flatMap { (_, instance) -> instance.price.map { it.value.currency } }
        .toSet()

    // Temporary workaround for issue in core returning wrong prices if there was an issue
    // fetching prices from google billing library.
    if (currencies.isEmpty() || currencies.size > 1)
        return null

    // The prices coming from Google Play will have a single currency:
    return currencies.first()
}
