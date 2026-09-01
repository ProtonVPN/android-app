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

import com.protonvpn.android.BuildConfig
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.promooffers.data.ApiNotification
import com.protonvpn.android.promooffers.data.ApiNotificationActions
import com.protonvpn.android.promooffers.data.ApiNotificationIapAction
import com.protonvpn.android.promooffers.data.ApiNotificationOffer
import com.protonvpn.android.promooffers.data.ApiNotificationOfferButton
import com.protonvpn.android.promooffers.data.ApiNotificationOfferFullScreenImage
import com.protonvpn.android.promooffers.data.ApiNotificationOfferImageSource
import com.protonvpn.android.promooffers.data.ApiNotificationOfferPanel
import com.protonvpn.android.promooffers.data.ApiNotificationTypes
import com.protonvpn.android.ui.planupgrade.IapConstants
import com.protonvpn.android.ui.planupgrade.PaymentCycle
import com.protonvpn.android.ui.planupgrade.toISO8601
import com.protonvpn.android.ui.planupgrade.usecase.LoadPlansConfig
import com.protonvpn.android.utils.DefaultLocaleProvider
import dagger.Reusable
import me.proton.core.util.kotlin.equalsNoCase
import me.proton.core.util.kotlin.startsWith
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration.Companion.days

private val PROMO_ACTIVITY_PERIOD_START_MS =
    if (BuildConfig.BUILD_TYPE == "benchmarkRelease") 0L else TimeUnit.HOURS.toMillis(5)
private val PROMO_ACTIVITY_PERIOD_END_MS = TimeUnit.DAYS.toMillis(3)
private const val CAMPAIGN_NAME = "internal_intro_price"
private const val NOTIFICATION_REFERENCE_BANNER = "IntroPricePromoBanner"
private const val NOTIFICATION_REFERENCE_FULLSCREEN = "IntroPricePromoModal"
private const val PLAN_NAME = "vpn2022"
private const val OFFER_TAG = IapConstants.INTRO_PRICE_TAG
private val REPEAT_INTERVAL_MS = 70.days.inWholeMilliseconds
private val REPEAT_INTERVAL_MAX_JITTER_MS = 10.days.inWholeMilliseconds

private const val IMAGE_ASSETS_URL = "file:///android_asset/promooffers"
private const val ANY = "any"

fun ApiNotification.isIntroductoryPriceOffer(): Boolean =
    this.id.startsWith(CAMPAIGN_NAME)

@Reusable
class GenerateNotificationsForIntroductoryOffers @Inject constructor(
    private val isIapClientSidePromoFeatureFlagEnabled: IsIapClientSidePromoFeatureFlagEnabled,
    private val isIapClientSidePromoCyclicEnabled: IsIapClientSidePromoCyclicEnabled,
    private val currentUser: CurrentUser,
    private val getEligibleOffers: GetEligibleOffers,
    private val appFeaturesPrefs: AppFeaturesPrefs,
    private val locale: DefaultLocaleProvider,
    @param:WallClock private val clock: () -> Long,
) {

    private data class NotificationConfig(
        val paymentCycle: PaymentCycle,
        val language: String?,
        val country: String?,
        val priceCents: Int?,
        val currency: String?,
        val altText: String, // Shared between banner and modal.
        val buttonText: String,
    ) {
        val matchCount = arrayOf<Any?>(language, country, currency, priceCents).count { it != null }
    }

    suspend operator fun invoke(triggerCyclicPromos: Boolean): List<ApiNotification> {
        if (!isIapClientSidePromoFeatureFlagEnabled()) return emptyList()
        if (currentUser.vpnUser()?.isFreeUser != true) return emptyList()

        val nowMs = clock()
        val (isFirstPromo, baseTimestampMs) =
            getBaseTimestamp(triggerCyclicPromos && isIapClientSidePromoCyclicEnabled())
        if (baseTimestampMs + PROMO_ACTIVITY_PERIOD_END_MS < nowMs) return emptyList()

        val loadPlansConfig = LoadPlansConfig.WithOfferTag(OFFER_TAG, null)
        val introductoryOffers = getEligibleOffers(loadPlansConfig) ?: return emptyList()
        val paymentCycle = PaymentCycle.Month(1)

        val startTimeMs = baseTimestampMs + if (isFirstPromo) PROMO_ACTIVITY_PERIOD_START_MS else 0L
        val startTimeS = TimeUnit.MILLISECONDS.toSeconds(startTimeMs)
        val endTimeS = TimeUnit.MILLISECONDS.toSeconds(baseTimestampMs + PROMO_ACTIVITY_PERIOD_END_MS)
        val userLocale = locale()
        val userLanguage = userLocale.language
        val userCountry = userLocale.country

        if (BuildConfig.DEBUG) {
            val offersLog = introductoryOffers.joinToString("; ") {
                with(it) { "$planName $cycle $currentPriceCents $currency" }
            }
            ProtonLogger.logCustom(
                LogLevel.DEBUG,
                LogCategory.PROMO,
                "Filtering offers for ${userLanguage}_${userCountry}; all intro price offers: [$offersLog]"
            )
        }
        return introductoryOffers.flatMap { playOffer ->
            if (playOffer.planName == PLAN_NAME && playOffer.cycle == paymentCycle) {

                val notification = notificationConfigs.filter { notification ->
                    notification.matches(
                        paymentCycle = paymentCycle,
                        language = userLanguage,
                        country = userCountry,
                        priceCents = playOffer.currentPriceCents,
                        currency = playOffer.currency
                    )
                }.maxByOrNull { it.matchCount }

                if (notification != null) {
                    listOf(
                        ApiNotificationTypes.TYPE_HOME_SCREEN_BANNER,
                        ApiNotificationTypes.TYPE_INTERNAL_ONE_TIME_IAP_POPUP
                    ).map { type ->
                        buildNotification(
                            type,
                            PLAN_NAME,
                            notification,
                            startTimeS,
                            endTimeS
                        )
                    }
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }.also { notifications ->
            val startDelta = startTimeS - nowMs / 1_000
            val endDelta = endTimeS - nowMs / 1_000
            val info = notifications.joinToString("; ") { n ->
                "type: ${n.type}, ${n.id}, time: [${startDelta}s, ${endDelta}s]"
            }
            ProtonLogger.logCustom(LogLevel.DEBUG, LogCategory.PROMO, "Intro price notifications [$info]")
        }
    }

    private fun NotificationConfig.matches(
        paymentCycle: PaymentCycle,
        language: String,
        country: String,
        priceCents: Int,
        currency: String
    ): Boolean {
        infix fun String?.matches(other: String) = this == null || this equalsNoCase other

        return this.paymentCycle == paymentCycle &&
            (this.priceCents == null || this.priceCents == priceCents) &&
            this.language matches language &&
            this.country matches country &&
            this.currency matches currency
    }

    private fun buildImageName(
        typeToken: String,
        planName: String,
        config: NotificationConfig
    ): String = arrayOf<String?>(
        CAMPAIGN_NAME,
        typeToken,
        planName,
        config.paymentCycle.toISO8601().lowercase(),
        config.currency,
        config.priceCents?.toString(),
        config.language,
        config.country,
    )
        .joinToString("_") { it?.lowercase() ?: ANY }

    private fun buildNotification(
        type: Int,
        planName: String,
        config: NotificationConfig,
        startTimeS: Long,
        endTimeS: Long,
    ): ApiNotification {
        val apiNotificationOffer: ApiNotificationOffer
        var notificationReference: String
        val typeToken: String
        when (type) {
            ApiNotificationTypes.TYPE_HOME_SCREEN_BANNER -> {
                typeToken = "banner"
                notificationReference = NOTIFICATION_REFERENCE_BANNER
                val iapPanel = buildNotificationPanel(planName, "modal", config)
                apiNotificationOffer = ApiNotificationOffer(
                    panel = buildNotificationPanel(
                        planName,
                        typeToken,
                        config,
                        buttonPanel = iapPanel,
                        showCountdown = true,
                        isDismissible = false,
                    )
                )
            }
            ApiNotificationTypes.TYPE_INTERNAL_ONE_TIME_IAP_POPUP -> {
                typeToken = "modal"
                notificationReference = NOTIFICATION_REFERENCE_FULLSCREEN
                apiNotificationOffer = ApiNotificationOffer(
                    panel = buildNotificationPanel(planName, typeToken, config)
                )
            }
            else -> throw IllegalArgumentException("Unsupported type $type")
        }

        notificationReference += config.paymentCycle.toISO8601()
        val notificationId = "${CAMPAIGN_NAME}_${typeToken}_${startTimeS}"
        return ApiNotification(
            id = notificationId,
            startTime = startTimeS,
            endTime = endTimeS,
            type = type,
            offer = apiNotificationOffer,
            reference = notificationReference,
        )
    }

    private fun buildNotificationPanel(
        planName: String,
        typeToken: String,
        config: NotificationConfig,
        buttonPanel: ApiNotificationOfferPanel? = null,
        showCountdown: Boolean = false,
        isDismissible: Boolean = true,
    ): ApiNotificationOfferPanel {
        val imageString = buildImageName(typeToken, planName, config)
        return ApiNotificationOfferPanel(
            fullScreenImage = ApiNotificationOfferFullScreenImage(
                source = listOf(
                    ApiNotificationOfferImageSource(
                        url = "$IMAGE_ASSETS_URL/${imageString}_dark.png",
                        urlLight = "$IMAGE_ASSETS_URL/${imageString}_light.png",
                        type = "png",
                    )
                ),
                alternativeText = config.altText,
            ),
            button = ApiNotificationOfferButton(
                text = config.buttonText,
                action = ApiNotificationActions.IN_APP_PURCHASE_POPUP,
                iapActionDetails = ApiNotificationIapAction(
                    planName = planName,
                    cycle = config.paymentCycle,
                    priceCents = config.priceCents,
                    currency = config.currency,
                    offerTag = OFFER_TAG,
                ),
                panel = buttonPanel
            ),
            showCountdown = showCountdown,
            isDismissible = isDismissible,
        )
    }

    private fun getBaseTimestamp(isCyclicEnabled: Boolean): Pair<Boolean, Long> {
        val now = clock()
        var startTimestamp = with(appFeaturesPrefs) {
            max(iapFirstIntroPriceCheckTimestamp, iapLastIntroPriceBaseTimestamp)
        }
        val generateNewBaseTimestamp: Boolean = when {
            startTimestamp == 0L -> true
            isCyclicEnabled -> {
                // Pseudo-randomly pick a jitter value:
                val jitterMs = abs(startTimestamp.hashCode().toLong()) % REPEAT_INTERVAL_MAX_JITTER_MS
                startTimestamp + jitterMs + REPEAT_INTERVAL_MS < now
            }
            else -> false
        }
        if (generateNewBaseTimestamp) {
            startTimestamp = now
            with(appFeaturesPrefs) {
                iapLastIntroPriceBaseTimestamp = now
                if (appFeaturesPrefs.iapFirstIntroPriceCheckTimestamp == 0L)
                    iapFirstIntroPriceCheckTimestamp = now
            }
        }

        val isFirst = with(appFeaturesPrefs) {
            iapLastIntroPriceBaseTimestamp == iapFirstIntroPriceCheckTimestamp
        }
        return Pair(isFirst, startTimestamp)
    }

    companion object {
        private val notificationConfigs = arrayOf(
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                null, null, null, null,
                altText = "Special offer. Try VPN Plus for less.",
                buttonText = "Claim offer",
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "en", null, 99, "usd",
                altText = "Special offer. Try VPN Plus for less.",
                buttonText = "Claim offer"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "en", "gb", 99, "gbp",
                altText = "Special offer. Try VPN Plus for less.",
                buttonText = "Claim offer"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "en", "ca", 99, "cad",
                altText = "Special offer. Try VPN Plus for less.",
                buttonText = "Claim offer"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "en", "au", 99, "aud",
                altText = "Special offer. Try VPN Plus for less.",
                buttonText = "Claim offer"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "fr", "ch", 99, "chf",
                altText = "Offre spéciale. Essayez VPN Plus pour moins cher.",
                buttonText = "Profiter de l'offre"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "fr", "fr", 99, "eur",
                altText = "Offre spéciale. Essayez VPN Plus pour moins cher.",
                buttonText = "Profiter de l'offre"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "de", "de", 99, "eur",
                altText = "Sonderangebot. Teste VPN Plus günstiger.",
                buttonText = "Angebot sichern"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "de", "ch", 99, "chf",
                altText = "Sonderangebot. Teste VPN Plus günstiger.",
                buttonText = "Angebot sichern"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "cz", "cz", null, null,
                altText = "Speciální nabídka. Vyzkoušejte VPN Plus levněji.",
                buttonText = "Využít nabídku"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "es", "es", 99, "eur",
                altText = "Oferta especial. Prueba VPN Plus por menos.",
                buttonText = "Solicitar oferta"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "es", null, null, null,
                altText = "Oferta especial. Pruebe VPN Plus por menos",
                buttonText = "Reclamar oferta"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "it", "it", 99, "eur",
                altText = "Offerta speciale. Prova VPN Plus a meno.",
                buttonText = "Ottieni offerta"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "nl", "nl", 99, "eur",
                altText = "Speciale aanbieding. Probeer VPN Plus voor minder.",
                buttonText = "Aanbieding claimen"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "pl", "pl", 99, "pln",
                altText = "Oferta specjalna. Wypróbuj VPN Plus taniej.",
                buttonText = "Odbierz ofertę"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "ru", "ru", null, null,
                altText = "Специальное предложение. Попробуйте VPN Plus по выгодной цене.",
                buttonText = "Воспользоваться"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "pt", "br", 99, "brl",
                altText = "Oferta especial. Experimente o VPN Plus por menos.",
                buttonText = "Resgatar oferta"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Month(1),
                "tr", "tr", null, null,
                altText = "Özel teklif. VPN Plus'ı daha uygun fiyata deneyin.",
                buttonText = "Teklifi Alın"
            ),

            // 12 month offers
            NotificationConfig(
                paymentCycle = PaymentCycle.Year(1),
                null, null, null, null,
                altText = "Special offer. Try VPN Plus for less.",
                buttonText = "Claim offer",
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Year(1),
                "cz", null, null, null,
                altText = "Speciální nabídka. Vyzkoušejte VPN Plus levněji.",
                buttonText = "Využít nabídku"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Year(1),
                "de", null, null, null,
                altText = "Sonderangebot. Teste VPN Plus günstiger.",
                buttonText = "Angebot sichern",
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Year(1),
                "es", null, null, null,
                altText = "Oferta especial. Pruebe VPN Plus por menos",
                buttonText = "Reclamar oferta",
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Year(1),
                "es", "es", null, null,
                altText = "Oferta especial. Prueba VPN Plus por menos.",
                buttonText = "Solicitar oferta",
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Year(1),
                "fr", null, null, null,
                altText = "Offre spéciale. Essayez VPN Plus pour moins cher.",
                buttonText = "Profiter de l'offre",
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Year(1),
                "it", null, null, null,
                altText = "Offerta speciale. Prova VPN Plus a meno.",
                buttonText = "Ottieni offerta",
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Year(1),
                "nl", null, null, null,
                altText = "Speciale aanbieding. Probeer VPN Plus voor minder.",
                buttonText = "Aanbieding claimen",
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Year(1),
                "pl", null, null, null,
                altText = "Oferta specjalna. Wypróbuj VPN Plus taniej.",
                buttonText = "Odbierz ofertę",
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Year(1),
                "pt", null, null, null,
                altText = "Oferta especial. Experimente o VPN Plus por menos.",
                buttonText = "Resgatar oferta"
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Year(1),
                "ru", null, null, null,
                altText = "Специальное предложение. Попробуйте VPN Plus по выгодной цене.",
                buttonText = "Воспользоваться",
            ),
            NotificationConfig(
                paymentCycle = PaymentCycle.Year(1),
                "tr", null, null, null,
                altText = "Özel teklif. VPN Plus'ı daha uygun fiyata deneyin.",
                buttonText = "Teklifi Alın",
            ),
        )
    }
}
