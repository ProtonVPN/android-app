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
import com.protonvpn.android.ui.planupgrade.PaymentCycle
import me.proton.android.payment.common.model.Money
import me.proton.android.payment.product.model.BillingCycle
import me.proton.android.payment.product.model.BillingRecurrence
import me.proton.android.payment.product.model.Offer
import me.proton.android.payment.product.sampledata.PricingPeriodSampleData
import me.proton.android.payment.product.sampledata.ProductSampleData

fun createProduct(
    id: String,
    planName: String,
    offers: List<Offer> = listOf(createOffer(PaymentCycle.Month(1), listOf(99))),
) = ProductSampleData.create(
    id = id,
    planId = planName,
    offers = offers,
)

fun createOffersWithDiscount(
    paymentCycle: PaymentCycle,
    discountPriceCents: Int,
    basePriceCents: Int,
    currency: String = "EUR",
    discountTag: String = IapConstants.INTRO_PRICE_TAG,
    offerCycleCount: Int = 1,
) = listOf(
    createOffer(
        paymentCycle = paymentCycle,
        pricesCents = listOf(discountPriceCents, basePriceCents),
        currency = currency,
        tags = listOf(discountTag),
        offerCycleCount = offerCycleCount
    ),
    createOffer(paymentCycle, listOf(basePriceCents), currency, emptyList())
)

fun createOffer(
    paymentCycle: PaymentCycle,
    pricesCents: List<Int>,
    currency: String = "EUR",
    tags: List<String>? = null,
    offerCycleCount: Int = 1,
    token: String = "dummy-token"
): Offer {
    val hasIntroPrice = pricesCents.size > 1
    val offerTags = when {
        tags != null -> tags
        hasIntroPrice -> listOf(IapConstants.INTRO_PRICE_TAG)
        else -> emptyList()
    }
    val billingCycle = when (paymentCycle) {
        is PaymentCycle.Day -> BillingCycle.Day(paymentCycle.count)
        is PaymentCycle.Week -> BillingCycle.Week(paymentCycle.count)
        is PaymentCycle.Month -> BillingCycle.Month(paymentCycle.count)
        is PaymentCycle.Year -> BillingCycle.Year(paymentCycle.count)
    }
    val pricingPeriods = pricesCents.mapIndexed { index, price ->
        val isLast = index == pricesCents.lastIndex
        val priceMicros = price.toLong() * 10_000

        PricingPeriodSampleData.create(
            price = Money(priceMicros, "${price.toFloat() / 100} $currency", currency),
            cycle = billingCycle,
            recurrence = if (isLast) BillingRecurrence.Infinite else BillingRecurrence.Finite(offerCycleCount),
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
