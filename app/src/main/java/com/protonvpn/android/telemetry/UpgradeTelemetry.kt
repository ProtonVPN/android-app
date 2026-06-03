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

package com.protonvpn.android.telemetry

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.promooffers.usecase.HasAnyIntroOffer
import com.protonvpn.android.promooffers.usecase.IsIapClientSidePromo12mExperimentEnabled
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.telemetry.CommonDimensions.Companion.NO_VALUE
import com.protonvpn.android.ui.planupgrade.UpgradeFlowType
import com.protonvpn.android.ui.planupgrade.comparison_table.IsUpsellComparisonTableEnabled
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.getValue
import com.protonvpn.android.utils.ifOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.core.domain.entity.UserId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class UpgradeSource {
    ACCOUNT,
    ADVANCED_CUSTOMIZATION,
    CHANGE_SERVER,
    COUNTRIES,
    DEVICES,
    NETSHIELD,
    ONBOARDING,
    P2P,
    TOR,
    PORT_FORWARDING,
    PROFILES,
    SECURE_CORE,
    SPLIT_TUNNELING,
    STREAMING,
    STREAMING_ACTIVITY,
    VPN_ACCELERATOR,
    PROMO_OFFER;

    val reportedName = name.lowercase()
}

enum class UpgradeTrigger {
    COUNTRIES_BANNER,
    COUNTRY_SELECTION,
    ERROR_DIALOG,
    HOME,
    HOME_BANNER,
    HOME_CAROUSEL,
    NETWORK_RESTRICTION,
    ONBOARDING,
    PROFILES,
    PROMO_OFFER_BANNER,
    PROMO_OFFER_POPUP,
    SEARCH,
    SEARCH_SELECTION,
    SETTINGS;

    val reportedName = name.lowercase()
}

enum class AbTestComparisonTable(val reportedValue: String) {
    CONTROL("control"), COMPARISON_TABLE("comparison_table");

    companion object {
        fun fromFf(isComparisonTableEnabled: Boolean) =
            if (isComparisonTableEnabled) COMPARISON_TABLE else CONTROL
    }
}

enum class AbTest12mPromo(val reportedValue: String) {
    CONTROL("control"), YEARLY("12m");

    companion object {
        fun fromUserId(userId: UserId) =
            if (userId.id.hashCode() % 2 == 0) CONTROL else YEARLY
    }
}

@Singleton
class UpgradeTelemetry @Inject constructor(
    private val mainScope: CoroutineScope,
    private val commonDimensions: CommonDimensions,
    private val currentUser: CurrentUser,
    @WallClock private val clock: () -> Long,
    telemetryHelperLazy: dagger.Lazy<TelemetryFlowHelper>,
    private val hasAnyIntroOffer: HasAnyIntroOffer,
    private val isUpsellComparisonTableEnabled: IsUpsellComparisonTableEnabled,
    private val isIapClientSidePromo12MExperimentEnabled: IsIapClientSidePromo12mExperimentEnabled,
) {
    private val helper by telemetryHelperLazy

    private var currentUpgradeFlow: UpgradeFlow? = null
    private val currentDimensions get() = currentUpgradeFlow?.getCurrentDimensions()
    private var isEligibleFor12mExperiment: Boolean? = null

    fun start() {
        combine(
            currentUser.vpnUserFlow,
            isIapClientSidePromo12MExperimentEnabled.observe()
        ) { vpnUser, isEnabled -> vpnUser?.isFreeUser == true && isEnabled }
            .distinctUntilChanged()
            .onEach { isExperimentEnabled ->
                if (isExperimentEnabled) {
                    onExperimentStarted()
                } else {
                    resetExperiment()
                }
            }
            .launchIn(mainScope)
    }

    private fun onExperimentStarted() {
        helper.runSerially {
            if (isEligibleFor12mExperiment != null) return@runSerially
            isEligibleFor12mExperiment = hasAnyIntroOffer(listOf(Constants.CURRENT_PLUS_PLAN))
            event12mExperiment("experiment_enrolled", emptyMap())
        }
    }

    private fun resetExperiment() {
        helper.runSerially {
            isEligibleFor12mExperiment = null
        }
    }

    fun onUpgradeFlowStarted(
        upgradeSource: UpgradeSource,
        upgradeTrigger: UpgradeTrigger,
        countryId: CountryId? = null,
        reference: String? = null
    ) {
        helper.runSerially {
            val abTestComparisonTableGroup = AbTestComparisonTable.fromFf(isUpsellComparisonTableEnabled())
            val dimensions = createDimensions(
                upgradeSource,
                upgradeTrigger,
                countryId,
                reference,
                abTestComparisonTableGroup,
            )
            currentUpgradeFlow = UpgradeFlow(dimensions, clock)
            event(eventData("upsell_display", dimensions))

            event12mExperiment("upsell_display", dimensions)
        }
    }

    fun onPricesLoaded(hasIntroPrices: Boolean) {
        helper.runSerially {
            currentUpgradeFlow?.update { it + Pair("has_intro_price", hasIntroPrices.toTelemetry()) }
            currentDimensions?.let {
                event(eventData("upsell_price_display", it))
                event12mExperiment("upsell_price_display", it)
            }
        }
    }

    fun onUpgradeAttempt(flowType: UpgradeFlowType) {
        helper.runSerially {
            currentDimensions?.let { currentDimensions ->
                event(eventData("upsell_upgrade_attempt", currentDimensions.withFlowType(flowType)))
                event12mExperiment("upsell_upgrade_attempt", currentDimensions)
            }
        }
    }

    fun onUpgradeSuccess(newPlanId: String?, flowType: UpgradeFlowType, billingCycle: Int) {
        helper.runSerially {
            currentDimensions?.let { currentDimensions ->
                val upgradedPlan = newPlanId ?: NO_VALUE
                val dimensions = currentDimensions + mapOf(
                    "upgraded_user_plan" to upgradedPlan,
                    "billing_cycle" to billingCycle.toString(),
                )
                currentUpgradeFlow = null
                event(eventData("upsell_success", dimensions.withFlowType(flowType)))
                event12mExperiment("upsell_success", dimensions)
            }
        }
    }

    private fun eventData(eventName: String, dimensions: Map<String, String>) =
        TelemetryEventData(MEASUREMENT_GROUP, eventName, emptyMap(), dimensions)

    private suspend fun createDimensions(
        upgradeSource: UpgradeSource,
        upgradeTrigger: UpgradeTrigger,
        countryId: CountryId?,
        reference: String?,
        abTestComparisonTableGroup: AbTestComparisonTable,
    ): Map<String, String> = buildMap {
        val user = currentUser.user()
        val vpnUser = currentUser.vpnUser()

        commonDimensions.add(this, CommonDimensions.Key.USER_COUNTRY_LEGACY, CommonDimensions.Key.VPN_STATUS_LEGACY,
            CommonDimensions.Key.USER_TIER_LEGACY, CommonDimensions.Key.IS_CREDENTIAL_LESS_ENABLED_LEGACY)
        put("modal_source", upgradeSource.reportedName)
        put("modal_trigger", upgradeTrigger.reportedName)
        put("new_free_plan_ui", "yes") // Used to be a feature flag.
        put("reference", reference ?: NO_VALUE)
        put("vpn_upsell_modal_comparison_table_20260427", abTestComparisonTableGroup.reportedValue)

        if (countryId != null && !countryId.isFastest) {
            put("country", countryId.countryCode)
        }

        if (user != null && vpnUser != null) {
            val timeSinceCreation = (clock() - user.createdAtUtc).milliseconds.takeIf { user.createdAtUtc > 0 }
            put("days_since_account_creation", accountCreationBucket(timeSinceCreation))
            put("user_plan", vpnUser.planName ?: NO_VALUE)
        }
    }

    private suspend fun TelemetryFlowHelper.RunSeriallyScope.event12mExperiment(
        eventName: String,
        dimensions: Map<String, String>,
    ) {
        val group = ifOrNull(isEligibleFor12mExperiment == true) {
            currentUser.vpnUser()?.let { AbTest12mPromo.fromUserId(it.userId) }
        }
        if (group != null) {
            val experimentVariant: Pair<String, String> = "experiment_variant" to group.reportedValue
            event(
                measurementGroup = EXPERIMENT_12M_MEASUREMENT_GROUP,
                event = eventName,
                dimensions = dimensions + experimentVariant
            )
        }
    }

    private fun Map<String, String>.withFlowType(flowType: UpgradeFlowType) =
        this + ("flow_type" to flowType.toStatsName())

    private fun accountCreationBucket(timeSinceCreation: Duration?): String {
        if (timeSinceCreation == null) return NO_VALUE

        val days = timeSinceCreation.inWholeDays
        return when {
            days == 0L -> "0"
            days <= 3 -> "1-3"
            days <= 7 -> "4-7"
            days <= 14 -> "8-14"
            else -> ">14"
        }
    }

    private class UpgradeFlow(
        private var dimensions: Map<String, String>,
        @WallClock private val clock: () -> Long
    ) {
        private val timestamp = clock()

        fun getCurrentDimensions() = dimensions.takeIf { timestamp + UPGRADE_FLOW_VALID_MS >= clock() }

        fun update(transform: (Map<String, String>) -> Map<String, String>) {
            dimensions = transform(dimensions)
        }
    }

    companion object {
        private const val MEASUREMENT_GROUP = "vpn.any.upsell"
        private const val EXPERIMENT_12M_MEASUREMENT_GROUP = "vpn.any.experiment_12m_promo_202605"
        private val UPGRADE_FLOW_VALID_MS = TimeUnit.MINUTES.toMillis(10)
    }
}
