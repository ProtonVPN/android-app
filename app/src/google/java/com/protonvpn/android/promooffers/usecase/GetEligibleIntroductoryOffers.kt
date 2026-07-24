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
import com.protonvpn.android.ui.planupgrade.IsInAppUpgradeAllowedUseCase
import com.protonvpn.android.ui.planupgrade.PlanCycle
import com.protonvpn.android.ui.planupgrade.usecase.LoadPlansConfig
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
import me.proton.android.payment.common.exception.PaymentException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

private val CacheDuration = 2.days

typealias DiscountOffersKey = LoadPlansConfig
typealias DiscountOffersCacheMap = Map<DiscountOffersKey, CachedOffers>

// TODO: rename, it's no longer about intro offers.
@Singleton
class GetEligibleIntroductoryOffers(
    private val loadSubscriptionPlans: LoadSubscriptionPlans,
    private val inAppUpgradeAllowed: IsInAppUpgradeAllowedUseCase,
    cacheObjectStore: ObjectStore<DiscountOffersCacheMap>,
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
                    LoadPlansConfig.serializer(),
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
        val currentPriceCents: Int,
        val offerTags: List<String>,
    )

    @Serializable
    data class CachedOffers(
        val timestamp: Long,
        val offers: List<Offer>
    )

    private class Cache(
        private val cacheObjectStore: ObjectStore<DiscountOffersCacheMap>,
    ) {

        private val mutex = Mutex()
        private var isLoaded = false
        private val cacheData = HashMap<DiscountOffersKey, CachedOffers>()

        // Only access when protected by the mutex.
        private suspend fun getCache(): HashMap<DiscountOffersKey, CachedOffers> {
            if (!isLoaded) {
                cacheData.putAll(cacheObjectStore.read() ?: emptyMap())
                isLoaded = true
            }
            return cacheData
        }

        suspend fun get(
            loadPlansConfig: LoadPlansConfig,
            now: Long
        ): CachedOffers? = mutex.withLock {
            getCache()[loadPlansConfig]
                ?.takeIf { it.timestamp + CacheDuration.inWholeMilliseconds > now }
        }

        suspend fun update(
            loadPlansConfig: LoadPlansConfig,
            timestamp: Long,
            offers: List<Offer>
        ) {
            mutex.withLock {
                val cache = getCache()
                cache[loadPlansConfig] = CachedOffers(timestamp, offers)
                cacheObjectStore.store(cache)
            }
        }
    }

    private val cache = Cache(cacheObjectStore)

    suspend operator fun invoke(
        loadPlansConfig: LoadPlansConfig,
    ): List<Offer>? {
        if (!inAppUpgradeAllowed()) return null

        val now = clock()
        val cachedOffers = cache.get(loadPlansConfig, now)
        return if (cachedOffers != null) {
            cachedOffers.offers
        } else {
            loadOffers(loadPlansConfig)
                ?.also { cache.update(loadPlansConfig, clock(), it)
            }
        }
    }

    private suspend fun loadOffers(
        loadPlansConfig: LoadPlansConfig,
    ): List<Offer>? = suspend {
        val subscriptionPlans = loadSubscriptionPlans(loadPlansConfig)
        val offers = subscriptionPlans.flatMap { plan ->
            plan.cycles.map { cycle ->
                Offer(
                    planName = plan.name,
                    cycle = cycle.cycle,
                    currency = plan.currency,
                    currentPriceCents = cycle.currentPriceCents,
                    offerTags = cycle.offerTags
                )
            }
        }
        offers
    }.runCatchingCheckedExceptions { e ->
        if (shouldReportToSentry(e)) {
            val code = if (e is PaymentException) e.code else null
            val message = "Error fetching offer prices${if (code != null) ", payments code: $code" else ""}."
            Sentry.captureException(GetIntroPricesError(message, e))
        }
        null
    }
}
