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

package com.protonvpn.android.ui.promooffers.usecase

import com.protonvpn.android.BuildConfig
import com.protonvpn.android.appconfig.ApiNotification
import com.protonvpn.android.appconfig.ApiNotificationActions
import com.protonvpn.android.appconfig.ApiNotificationIapAction
import com.protonvpn.android.appconfig.ApiNotificationOffer
import com.protonvpn.android.appconfig.ApiNotificationOfferButton
import com.protonvpn.android.appconfig.ApiNotificationOfferFullScreenImage
import com.protonvpn.android.appconfig.ApiNotificationOfferImageSource
import com.protonvpn.android.appconfig.ApiNotificationOfferPanel
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.DefaultLocaleProvider
import dagger.Reusable
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.util.kotlin.equalsNoCase
import me.proton.core.util.kotlin.startsWith
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private val PROMO_ACTIVITY_PERIOD_START_MS = TimeUnit.HOURS.toMillis(5)
private val PROMO_ACTIVITY_PERIOD_END_MS = TimeUnit.DAYS.toMillis(3)
private const val CAMPAIGN_NAME = "internal_intro_price"
private const val NOTIFICATION_REFERENCE_BANNER = "IntroPricePromoBanner"
private const val NOTIFICATION_REFERENCE_FULLSCREEN = "IntroPricePromoModal"
private const val PLAN_NAME = "vpn2022"
private val PLAN_CYCLE = PlanCycle.MONTHLY

private const val IMAGE_ASSETS_URL = "file:///android_asset/promooffers"
private const val ANY = "any"

fun ApiNotification.isIntroductoryPriceOffer(): Boolean =
    this.id.startsWith(CAMPAIGN_NAME)

@Reusable
class GenerateNotificationsForIntroductoryOffers @Inject constructor(
    private val isIapClientSidePromoFeatureFlagEnabled: IsIapClientSidePromoFeatureFlagEnabled,
    private val currentUser: CurrentUser,
    private val getEligibleIntroductoryOffers: GetEligibleIntroductoryOffers,
    private val appFeaturesPrefs: AppFeaturesPrefs,
    private val locale: DefaultLocaleProvider,
    @WallClock private val clock: () -> Long,
) {

    private data class NotificationConfig(
        val language: String?,
        val country: String?,
        val priceCents: Int?,
        val currency: String?,
        val altText: String, // Shared between banner and modal.
        val buttonText: String,
    ) {
        val matchCount = arrayOf<Any?>(language, country, currency, priceCents).count { it != null }
    }

    suspend operator fun invoke(): List<ApiNotification> {
        if (!isIapClientSidePromoFeatureFlagEnabled()) return emptyList()
        if (currentUser.vpnUser()?.isFreeUser != true) return emptyList()

        val allPlans = listOf(Constants.CURRENT_PLUS_PLAN, Constants.CURRENT_BUNDLE_PLAN)
        val nowMs = clock()
        val baseTimestampMs = getBaseTimestamp()
        if (baseTimestampMs + PROMO_ACTIVITY_PERIOD_END_MS < nowMs) return emptyList()
        val introductoryOffers = getEligibleIntroductoryOffers(allPlans) ?: return emptyList()

        val startTimesS = TimeUnit.MILLISECONDS.toSeconds(baseTimestampMs + PROMO_ACTIVITY_PERIOD_START_MS)
        val endTimeS = TimeUnit.MILLISECONDS.toSeconds(baseTimestampMs + PROMO_ACTIVITY_PERIOD_END_MS)
        val userLocale = locale()
        val userLanguage = userLocale.language
        val userCountry = userLocale.country

        if (BuildConfig.DEBUG) {
            val offersLog = introductoryOffers.joinToString("; ") {
                with(it) { "$planName $cycle $introPriceCents $currency" }
            }
            ProtonLogger.logCustom(
                LogLevel.DEBUG,
                LogCategory.PROMO,
                "Filtering offers for ${userLanguage}_${userCountry}; all intro price offers: [$offersLog]"
            )
        }
        return introductoryOffers.flatMap { playOffer ->
            if (playOffer.planName == PLAN_NAME && playOffer.cycle == PLAN_CYCLE) {

                val notification = notificationConfigs.filter { notification ->
                    notification.matches(
                        language = userLanguage,
                        country = userCountry,
                        priceCents = playOffer.introPriceCents,
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
                            PLAN_CYCLE,
                            notification,
                            startTimesS,
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
            val startDelta = startTimesS - nowMs / 1_000
            val endDelta = endTimeS - nowMs / 1_000
            val info = notifications.joinToString("; ") { n ->
                "type: ${n.type}, ${n.id}, time: [${startDelta}s, ${endDelta}s]"
            }
            ProtonLogger.logCustom(LogLevel.DEBUG, LogCategory.PROMO, "Intro price notifications [$info]")
        }
    }

    private fun NotificationConfig.matches(language: String, country: String, priceCents: Int, currency: String): Boolean {
        infix fun String?.matches(other: String) = this == null || this equalsNoCase other

        return (this.priceCents == null || this.priceCents == priceCents) &&
            this.language matches language &&
            this.country matches country &&
            this.currency matches currency
    }

    private fun buildImageName(
        typeToken: String,
        planName: String,
        planCycle: PlanCycle,
        config: NotificationConfig
    ): String = arrayOf<String?>(
        CAMPAIGN_NAME,
        typeToken,
        planName,
        planCycle.cycleDurationMonths.toString(),
        config.currency,
        config.priceCents?.toString(),
        config.language,
        config.country,
    )
        .joinToString("_") { it?.lowercase() ?: ANY }

    private fun buildNotification(
        type: Int,
        planName: String,
        planCycle: PlanCycle,
        config: NotificationConfig,
        startTimeS: Long,
        endTimeS: Long,
    ): ApiNotification {
        val notificationId: String
        val apiNotificationOffer: ApiNotificationOffer
        val notificationReference: String
        when (type) {
            ApiNotificationTypes.TYPE_HOME_SCREEN_BANNER -> {
                val typeToken = "banner"
                notificationId = "${CAMPAIGN_NAME}_${typeToken}"
                notificationReference = NOTIFICATION_REFERENCE_BANNER
                val iapPanel = buildNotificationPanel(planName, planCycle, "modal", config)
                apiNotificationOffer = ApiNotificationOffer(
                    panel = buildNotificationPanel(
                        planName,
                        planCycle,
                        typeToken,
                        config,
                        buttonPanel = iapPanel,
                        showCountdown = true,
                        isDismissible = false,
                    )
                )
            }
            ApiNotificationTypes.TYPE_INTERNAL_ONE_TIME_IAP_POPUP -> {
                val typeToken = "modal"
                notificationId = "${CAMPAIGN_NAME}_${typeToken}"
                notificationReference = NOTIFICATION_REFERENCE_FULLSCREEN
                apiNotificationOffer = ApiNotificationOffer(
                    panel = buildNotificationPanel(planName, planCycle, typeToken, config)
                )
            }
            else -> throw IllegalArgumentException("Unsupported type $type")
        }

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
        planCycle: PlanCycle,
        typeToken: String,
        config: NotificationConfig,
        buttonPanel: ApiNotificationOfferPanel? = null,
        showCountdown: Boolean = false,
        isDismissible: Boolean = true,
    ): ApiNotificationOfferPanel {
        val imageString = buildImageName(typeToken, planName, planCycle, config)
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
                    cycle = planCycle,
                    priceCents = config.priceCents,
                    currency = config.currency,
                ),
                panel = buttonPanel
            ),
            showCountdown = showCountdown,
            isDismissible = isDismissible,
        )
    }

    private fun getBaseTimestamp(): Long {
        val now = clock()
        var startTimestamp = appFeaturesPrefs.iapFirstIntroPriceCheckTimestamp
        if (startTimestamp == 0L) {
            startTimestamp = now
            appFeaturesPrefs.iapFirstIntroPriceCheckTimestamp = now
        }

        return startTimestamp
    }

    companion object {
        private val notificationConfigs = arrayOf(
            NotificationConfig(
                null, null, null, null,
                altText = "Special offer. Try VPN Plus for less.",
                buttonText = "Claim offer",
            ),
            NotificationConfig(
                "en", null, 99, "usd",
                altText = "Special offer. Try VPN Plus for less.",
                buttonText = "Claim offer"
            ),
            NotificationConfig(
                "en", "gb", 99, "gbp",
                altText = "Special offer. Try VPN Plus for less.",
                buttonText = "Claim offer"
            ),
            NotificationConfig(
                "en", "ca", 99, "cad",
                altText = "Special offer. Try VPN Plus for less.",
                buttonText = "Claim offer"
            ),
            NotificationConfig(
                "en", "au", 99, "aud",
                altText = "Special offer. Try VPN Plus for less.",
                buttonText = "Claim offer"
            ),
            NotificationConfig(
                "fr", "ch", 99, "chf",
                altText = "Offre spéciale. Essayez VPN Plus pour moins cher.",
                buttonText = "Profiter de l'offre"
            ),
            NotificationConfig(
                "fr", "fr", 99, "eur",
                altText = "Offre spéciale. Essayez VPN Plus pour moins cher.",
                buttonText = "Profiter de l'offre"
            ),
            NotificationConfig(
                "de", "de", 99, "eur",
                altText = "Sonderangebot. Teste VPN Plus günstiger.",
                buttonText = "Angebot sichern"
            ),
            NotificationConfig(
                "de", "ch", 99, "chf",
                altText = "Sonderangebot. Teste VPN Plus günstiger.",
                buttonText = "Angebot sichern"
            ),
            NotificationConfig(
                "cz", "cz", null, null,
                altText = "Speciální nabídka. Vyzkoušejte VPN Plus levněji.",
                buttonText = "Využít nabídku"
            ),
            NotificationConfig(
                "es", "es", 99, "eur",
                altText = "Oferta especial. Prueba VPN Plus por menos.",
                buttonText = "Solicitar oferta"
            ),
            NotificationConfig(
                "es", null, null, null,
                altText = "Oferta especial. Pruebe VPN Plus por menos",
                buttonText = "Reclamar oferta"
            ),
            NotificationConfig(
                "it", "it", 99, "eur",
                altText = "Offerta speciale. Prova VPN Plus a meno.",
                buttonText = "Ottieni offerta"
            ),
            NotificationConfig(
                "nl", "nl", 99, "eur",
                altText = "Speciale aanbieding. Probeer VPN Plus voor minder.",
                buttonText = "Aanbieding claimen"
            ),
            NotificationConfig(
                "pl", "pl", 99, "pln",
                altText = "Oferta specjalna. Wypróbuj VPN Plus taniej.",
                buttonText = "Odbierz ofertę"
            ),
            NotificationConfig(
                "ru", "ru", null, null,
                altText = "Специальное предложение. Попробуйте VPN Plus по выгодной цене.",
                buttonText = "Воспользоваться"
            ),
            NotificationConfig(
                "pt", "br", 99, "brl",
                altText = "Oferta especial. Experimente o VPN Plus por menos.",
                buttonText = "Resgatar oferta"
            ),
            NotificationConfig(
                "tr", "tr", null, null,
                altText = "Özel teklif. VPN Plus'ı daha uygun fiyata deneyin.",
                buttonText = "Teklifi Alın"
            ),
        )
    }
}
