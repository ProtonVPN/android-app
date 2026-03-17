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
import com.protonvpn.android.promooffers.usecase.GetEligibleIntroductoryOffers.CachedOffers
import com.protonvpn.android.ui.planupgrade.IsInAppUpgradeAllowedUseCase
import com.protonvpn.android.ui.planupgrade.getSingleCurrency
import com.protonvpn.android.ui.planupgrade.usecase.LoadGoogleSubscriptionPlans
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
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.ApiResult
import me.proton.core.plan.presentation.entity.PlanCycle
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

private val CacheDuration = 2.days

typealias IntroductoryOffersCacheMap = Map<String, CachedOffers>

@Singleton
class GetEligibleIntroductoryOffers(
    private val loadGoogleSubscriptionPlans: LoadGoogleSubscriptionPlans,
    private val inAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase,
    cacheObjectStore: ObjectStore<IntroductoryOffersCacheMap>,
    private val clock: () -> Long,
) {
    @Inject
    constructor(
        mainScope: CoroutineScope,
        @ApplicationContext context: Context,
        dispatcherProvider: VpnDispatcherProvider,
        loadGoogleSubscriptionPlans: LoadGoogleSubscriptionPlans,
        inAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase,
        @WallClock clock: () -> Long,
    ) : this(
        loadGoogleSubscriptionPlans,
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
            val giapPlans = loadGoogleSubscriptionPlans(planNames)

            val introOffers = giapPlans.flatMap { plan ->
                val currency = plan.dynamicPlan.getSingleCurrency() ?: return@flatMap emptyList()

                plan.cycles.mapNotNull { cycle ->
                    val planInstance = plan.dynamicPlan.instances[cycle.cycle.cycleDurationMonths]
                    val price = planInstance?.price?.get(currency)
                    val currentPriceCents = price?.current
                    val renewPriceCents = price?.default

                    if (currentPriceCents != null && renewPriceCents != null && currentPriceCents < renewPriceCents) {
                        Offer(
                            planName = plan.name,
                            cycle = cycle.cycle,
                            currency = currency,
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
            if (shouldReportToSentry(e))
                Sentry.captureException(GetIntroPricesError("Error fetching intro prices", e))
            null
        }
    }

    private fun shouldReportToSentry(throwable: Throwable?): Boolean =
        throwable == null || (throwable as? ApiException)?.error !is ApiResult.Error.Connection
}

private class GetIntroPricesError(message: String, cause: Throwable) : Exception(message, cause)
