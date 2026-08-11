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

package com.protonvpn.android.promooffers.usecase

import android.content.Context
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.promooffers.GetIntroPricesError
import com.protonvpn.android.promooffers.usecase.GetEligibleIntroductoryOffers.CachedOffers
import com.protonvpn.android.ui.planupgrade.IapConstants
import com.protonvpn.android.ui.planupgrade.IsInAppUpgradeAllowedUseCase
import com.protonvpn.android.ui.planupgrade.PlanCycle
import com.protonvpn.android.ui.planupgrade.usecase.LoadSubscriptionPlans
import com.protonvpn.android.ui.planupgrade.usecase.shouldReportToSentry
import com.protonvpn.android.utils.BytesFileWriter
import com.protonvpn.android.utils.FileObjectStore
import com.protonvpn.android.utils.KotlinCborObjectSerializer
import com.protonvpn.android.utils.ObjectStore
import com.protonvpn.android.utils.runCatchingCheckedExceptions
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import me.proton.android.payment.common.exception.PaymentException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

private val CacheDuration = 2.days

typealias IntroductoryOffersCacheMap = Map<String, CachedOffers>

@Singleton
class GetEligibleIntroductoryOffers(
    private val loadSubscriptionPlans: LoadSubscriptionPlans,
    private val inAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase,
    cacheObjectStore: ObjectStore<IntroductoryOffersCacheMap>,
    private val clock: () -> Long,
) {
    @Inject
    constructor(
        mainScope: CoroutineScope,
        @ApplicationContext context: Context,
        dispatcherProvider: VpnDispatcherProvider,
        loadSubscriptionPlans: LoadSubscriptionPlans,
        inAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase,
        @WallClock clock: () -> Long,
    ) : this(
        loadSubscriptionPlans,
        inAppUpgradeAllowed,
        FileObjectStore(
            File(context.filesDir, "intro_price_eligible_offers_cache"),
            mainScope,
            dispatcherProvider,
            KotlinCborObjectSerializer(
                MapSerializer(
                    String.serializer(),
                    CachedOffers.serializer()
                )
            ),
            BytesFileWriter()
        ),
        clock,
    )

    @Serializable
    data class Offer(
        val planName: String,
        val cycle: PlanCycle,
        val currency: String,
        val introPriceCents: Int
    )

    @Serializable
    data class CachedOffers(
        val timestamp: Long,
        val offers: List<Offer>
    )

    private class Cache(
        private val cacheObjectStore: ObjectStore<IntroductoryOffersCacheMap>,
    ) {

        private val mutex = Mutex()
        private var isLoaded = false
        private val cacheData = HashMap<String, CachedOffers>()

        // Only access when protected by the mutex.
        private suspend fun getCache(): HashMap<String, CachedOffers> {
            if (!isLoaded) {
                cacheData.putAll(cacheObjectStore.read() ?: emptyMap())
                isLoaded = true
            }
            return cacheData
        }

        suspend fun get(planName: String, now: Long): CachedOffers? = mutex.withLock {
            getCache().get(planName)
                ?.takeIf { it.timestamp + CacheDuration.inWholeMilliseconds > now }
        }

        suspend fun update(planNames: List<String>, timestamp: Long, offers: List<Offer>) {
            mutex.withLock {
                val cache = getCache()
                planNames.forEach { planName ->
                    cache[planName] =
                        CachedOffers(timestamp, offers.filter { it.planName == planName })
                }
                cacheObjectStore.store(cache)
            }
        }
    }

    private val cache = Cache(cacheObjectStore)

    suspend operator fun invoke(planNames: List<String>): List<Offer>? {
        if (!inAppUpgradeAllowed()) return null

        val now = clock()
        val cachedOffers = planNames.mapNotNull {
            cache.get(it, now)
        }
        return if (planNames.size == cachedOffers.size) {
            cachedOffers.flatMap { it.offers }
        } else suspend {
            val giapPlans = loadSubscriptionPlans(planNames, IapConstants.INTRO_PRICE_TAG)

            val introOffers = giapPlans.flatMap { plan ->
                plan.cycles.mapNotNull { cycle ->
                    val currentPriceCents = cycle.currentPriceCents
                    val renewPriceCents = cycle.defaultPriceCents

                    // Note: the prices will be equal if there is just one pricing phase, let's
                    // be conservative and require 2 pricing phases to display the offer.
                    if (currentPriceCents < renewPriceCents) {
                        Offer(
                            planName = plan.name,
                            cycle = cycle.cycle,
                            currency = plan.currency,
                            introPriceCents = currentPriceCents
                        )
                    } else {
                        null
                    }
                }
            }
            cache.update(planNames, clock(), introOffers)
            introOffers
        }.runCatchingCheckedExceptions { e ->
            if (shouldReportToSentry(e)) {
                val code = if (e is PaymentException) e.code else null
                val message = "Error fetching offer prices${if (code != null) ", payments code: $code" else ""}."
                Sentry.captureException(GetIntroPricesError(message, e))
            }
            null
        }
    }
}

