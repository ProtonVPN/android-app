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
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.ui.planupgrade.PlanCycle
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.getValue
import com.protonvpn.android.utils.ifOrNull
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.proton.android.payment.common.model.PlanId
import me.proton.android.payment.common.model.ProductId
import me.proton.android.payment.product.model.BillingCycle
import me.proton.android.payment.product.model.Offer
import me.proton.android.payment.product.model.Product
import me.proton.android.payment.product.usecase.GetProducts
import javax.inject.Inject

data class CycleInfo(
    val cycle: PlanCycle,
    val productId: String,
    val offerToken: String,
    val currentPriceCents: Int,
    val defaultPriceCents: Int,
)

data class SubscriptionPlanInfo(
    val name: String,
    val displayName: String,
    val currency: String,
    val cycles: List<CycleInfo>,
    val preselectedCycle: PlanCycle,
)

@Reusable
class LoadSubscriptionPlans(
    private val vpnUserFlow: Flow<VpnUser?>,
    getProductsLazy: dagger.Lazy<GetProducts>,
    private val defaultCycles: List<PlanCycle>,
    private val defaultPreselectedCycle: PlanCycle
) {
    private val getProducts by getProductsLazy

    @Inject constructor(
        currentUser: CurrentUser,
        getProductsLazy: dagger.Lazy<GetProducts>,
    ) : this(
        vpnUserFlow = currentUser.vpnUserFlow,
        getProductsLazy = getProductsLazy,
        DEFAULT_CYCLES,
        DEFAULT_PRESELECTED_CYCLE
    )

    /**
     * Loads IAP subscriptions for given Proton plan names.
     * If discountedOfferTag is not null, selects offers with the given tag, if available.
     * Otherwise, the base plan price is used.
     */
    suspend operator fun invoke(
        planNames: List<String>,
        discountOfferTag: String?,
    ): List<SubscriptionPlanInfo> {
        val vpnUser = vpnUserFlow.first() ?: return emptyList()
        if (vpnUser.hasSubscription) {
            ProtonLogger.logCustom(LogCategory.IN_APP_PURCHASE, "IAP unavailable, user has a subscription")
            return emptyList()
        }

        return loadPlans(
            planNames = planNames,
            planCycles = defaultCycles,
            preselectedCycle = defaultPreselectedCycle,
            discountOfferTag = discountOfferTag,
        )
    }

    suspend fun loadPlans(
        planNames: List<String>,
        planCycles: List<PlanCycle>,
        preselectedCycle: PlanCycle,
        discountOfferTag: String?,
    ): List<SubscriptionPlanInfo> {
        val subscriptionPlans = getProducts().fold(
            onSuccess = { products ->
                products
                    .filter { product -> product.planId in planNames }
                    .groupBy { it.planId }
                    .mapNotNull { (planName, products) ->
                        var currency: String? = null
                        val allCycles = products.map { product ->
                            DebugUtils.debugAssert { product.offers.count { it is Offer.NonDiscounted } == 1 }
                            val baseOffer = product.offers.filterIsInstance<Offer.NonDiscounted>().first()
                            DebugUtils.debugAssert { baseOffer.pricingPeriods.isNotEmpty() }
                            val discountedOffer = ifOrNull(discountOfferTag != null) {
                                product.offers
                                    .filterIsInstance<Offer.Discounted>()
                                    .find { it.tags.contains(discountOfferTag) }
                            }

                            currency = baseOffer.pricingPeriods.get(0).price.currency
                            createCycleInfo(product.id, discountedOffer ?: baseOffer, baseOffer)
                        }

                        val cycles = allCycles.filter { it.cycle in planCycles }
                        if (cycles.isNotEmpty() && currency != null) {
                            createPlanInfo(cycles, preselectedCycle, planName, products, currency)
                        } else {
                            logWarning("plan '${planName}' has no Google products/offers.")
                            null
                        }
                    }
            },
            onFailure = { e ->
                throw e
            }
        )
        val firstPlanCurrency = subscriptionPlans.firstOrNull()?.currency
        val planWithDifferentCurrency = subscriptionPlans.find { it.currency != firstPlanCurrency }
        if (planWithDifferentCurrency != null) {
            throw IllegalArgumentException(
                "Conflicting currencies: $firstPlanCurrency vs ${planWithDifferentCurrency}."
            )
        }
        return subscriptionPlans
    }

    private fun createPlanInfo(
        cycles: List<CycleInfo>,
        preselectedCycle: PlanCycle,
        planName: PlanId?,
        products: List<Product>,
        currency: String,
    ): SubscriptionPlanInfo {
        val preselectedCycle = if (cycles.any { it.cycle == preselectedCycle }) {
            preselectedCycle
        } else {
            cycles.first().cycle
        }
        return SubscriptionPlanInfo(
            name = requireNotNull(planName),
            displayName = products.first().title,
            currency = currency,
            cycles = cycles,
            preselectedCycle = preselectedCycle,
        )
    }

    private fun createCycleInfo(productId: ProductId, offer: Offer, baseOffer: Offer.NonDiscounted): CycleInfo {
        val purchasePeriod = offer.pricingPeriods.first()
        val renewPeriod = baseOffer.pricingPeriods.first()
        val planCycle = purchasePeriod.cycle.toPlanCycle()
        logDebug("Product: $productId $planCycle, purchase offer: ${offer.tags} ${purchasePeriod}, renew offer: $renewPeriod")
        logDebug("Purchase offer phases: ${offer.pricingPeriods}")
        return CycleInfo(
            planCycle,
            productId,
            offer.token,
            (purchasePeriod.price.amount / SDK_AMOUNT_TO_CENTS_PRICE_DIVIDER).toInt(),
            (renewPeriod.price.amount / SDK_AMOUNT_TO_CENTS_PRICE_DIVIDER).toInt()
        )
    }

    private fun BillingCycle.toPlanCycle(): PlanCycle = when(this) {
        is BillingCycle.Month if count == 1 -> PlanCycle.MONTHLY
        is BillingCycle.Year if count == 1 -> PlanCycle.YEARLY
        is BillingCycle.Year if count == 2 -> PlanCycle.TWO_YEARS
        else -> PlanCycle.OTHER
    }

    private fun logDebug(message: String) {
        ProtonLogger.logCustom(LogLevel.DEBUG, LogCategory.IN_APP_PURCHASE, message)
    }

    private fun logWarning(message: String) {
        ProtonLogger.logCustom(LogLevel.WARN, LogCategory.IN_APP_PURCHASE, message)
    }

    companion object {
        // TODO: in future this should come from API
        private val DEFAULT_CYCLES = listOf(PlanCycle.MONTHLY, PlanCycle.YEARLY)
        private val DEFAULT_PRESELECTED_CYCLE = PlanCycle.YEARLY

        private const val SDK_AMOUNT_TO_CENTS_PRICE_DIVIDER = 10_000
    }
}
