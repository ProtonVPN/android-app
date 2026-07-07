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

package com.protonvpn.test.shared

import com.protonvpn.android.ui.planupgrade.IapConstants
import com.protonvpn.android.ui.planupgrade.PlanCycle
import me.proton.android.payment.common.model.Money
import me.proton.android.payment.product.model.BillingCycle
import me.proton.android.payment.product.model.BillingRecurrence
import me.proton.android.payment.product.model.Offer
import me.proton.android.payment.product.sampledata.PricingPeriodSampleData
import me.proton.android.payment.product.sampledata.ProductSampleData

fun createProduct(
    id: String,
    planName: String,
    offers: List<Offer> = listOf(createOffer(PlanCycle.MONTHLY, listOf(99))),
) = ProductSampleData.create(
    id = id,
    planId = planName,
    offers = offers,
)

fun createOffersWithDiscount(
    planCycle: PlanCycle,
    discountPriceCents: Int,
    basePriceCents: Int,
    currency: String = "EUR",
    discountTag: String = IapConstants.INTRO_PRICE_TAG,
) = listOf(
    createOffer(planCycle, listOf(discountPriceCents, basePriceCents), currency, listOf(discountTag)),
    createOffer(planCycle, listOf(basePriceCents), currency, emptyList())
)

fun createOffer(
    planCycle: PlanCycle,
    pricesCents: List<Int>,
    currency: String = "EUR",
    tags: List<String>? = null,
    token: String = "dummy-token"
): Offer {
    val hasIntroPrice = pricesCents.size > 1
    val offerTags = when {
        tags != null -> tags
        hasIntroPrice -> listOf(IapConstants.INTRO_PRICE_TAG)
        else -> emptyList()
    }
    val billingCycle = when (planCycle) {
        PlanCycle.MONTHLY -> BillingCycle.Month(1)
        PlanCycle.YEARLY -> BillingCycle.Year(1)
        PlanCycle.TWO_YEARS -> BillingCycle.Year(2)
        PlanCycle.OTHER -> throw IllegalArgumentException()
    }
    val pricingPeriods = pricesCents.mapIndexed { index, price ->
        val isLast = index == pricesCents.lastIndex
        val priceMicros = price.toLong() * 10_000

        PricingPeriodSampleData.create(
            price = Money(priceMicros, "${price.toFloat() / 100} $currency", currency),
            cycle = billingCycle,
            recurrence = if (isLast) BillingRecurrence.Infinite else BillingRecurrence.Finite(1),
        )
    }
    return if (hasIntroPrice) {
        Offer.Discounted(
            pricingPeriods = pricingPeriods,
            tags = offerTags,
            token = token,
        )
    } else {
        Offer.NonDiscounted(
            pricingPeriods = pricingPeriods,
            tags = offerTags,
            token = token,
        )
    }
}
