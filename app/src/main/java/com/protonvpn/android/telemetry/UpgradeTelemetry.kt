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
import com.protonvpn.android.ui.planupgrade.comparison_table.IsUpsellComparisonTableExperimentEnabled
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.getValue
import com.protonvpn.android.utils.ifOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.core.domain.entity.UserId
import java.security.MessageDigest
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
    DOWNGRADE,
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
        fun fromUserId(userId: UserId) =
            if (userId.isControlGroup()) CONTROL else COMPARISON_TABLE
    }
}

enum class AbTest12mPromo(val reportedValue: String) {
    CONTROL("control"), YEARLY("12m");

    companion object {
        fun fromUserId(userId: UserId) =
            if (userId.isControlGroup()) CONTROL else YEARLY
    }
}

private fun UserId.isControlGroup(): Boolean =
    id.toMd5().last() % 2 == 0


private fun String.toMd5(): ByteArray {
    val md5 = MessageDigest.getInstance("md5")
    md5.update(this.toByteArray())
    return md5.digest()
}

@Singleton
class UpgradeTelemetry @Inject constructor(
    private val mainScope: CoroutineScope,
    private val commonDimensions: CommonDimensions,
    private val currentUser: CurrentUser,
    @WallClock private val clock: () -> Long,
    telemetryHelperLazy: dagger.Lazy<TelemetryFlowHelper>,
    private val hasAnyIntroOffer: HasAnyIntroOffer,
    private val isUpsellComparisonTableExperimentEnabled: IsUpsellComparisonTableExperimentEnabled,
    private val isIapClientSidePromo12MExperimentEnabled: IsIapClientSidePromo12mExperimentEnabled,
) {
    private val helper by telemetryHelperLazy

    private var currentUpgradeFlow: UpgradeFlow? = null
    private val currentDimensions get() = currentUpgradeFlow?.getCurrentDimensions()
    private var isEligibleFor12mExperiment: Boolean? = null
    private var isComparisonTableExperiment: Boolean? = null

    fun start() {
        combine(
            currentUser.vpnUserFlow,
            isIapClientSidePromo12MExperimentEnabled.observe()
        ) { vpnUser, isEnabled -> vpnUser?.isFreeUser == true && isEnabled }
            .distinctUntilChanged()
            .onEach { isExperimentEnabled ->
                if (isExperimentEnabled) {
                    on12mExperimentStarted()
                } else {
                    reset12mExperiment()
                }
            }
            .launchIn(mainScope)

        combine(
            currentUser.vpnUserFlow,
            isUpsellComparisonTableExperimentEnabled.observe()
        ) { vpnUser, isEnabled -> vpnUser?.isFreeUser == true && isEnabled }
            .distinctUntilChanged()
            .onEach { isExperimentEnabled ->
                if (isExperimentEnabled) {
                    onComparisonTableExperimentStarted()
                } else {
                    resetComparisonTableExperiment()
                }
            }
            .launchIn(mainScope)
    }

    private fun on12mExperimentStarted() {
        helper.runSerially {
            if (isEligibleFor12mExperiment != null) return@runSerially
            isEligibleFor12mExperiment = hasAnyIntroOffer(listOf(Constants.CURRENT_PLUS_PLAN))
            eventExperiment12m("experiment_enrolled", emptyMap())
        }
    }

    private fun reset12mExperiment() {
        helper.runSerially {
            isEligibleFor12mExperiment = null
        }
    }

    private fun onComparisonTableExperimentStarted() {
        helper.runSerially {
            isComparisonTableExperiment = true
            eventExperimentComparisonTable(
                "experiment_enrolled",
                createBasicUpsellDimensions()
            )
        }
    }

    private fun resetComparisonTableExperiment() {
        helper.runSerially {
            isComparisonTableExperiment = false
        }
    }

    fun onUpgradeFlowStarted(
        upgradeSource: UpgradeSource,
        upgradeTrigger: UpgradeTrigger,
        countryId: CountryId? = null,
        reference: String? = null
    ) {
        helper.runSerially {
            val dimensions = createDimensions(
                upgradeSource,
                upgradeTrigger,
                countryId,
                reference,
            )
            currentUpgradeFlow = UpgradeFlow(dimensions, clock)
            event(eventData("upsell_display", dimensions))

            eventExperiments("upsell_display", dimensions)
        }
    }

    fun onPricesLoaded(hasIntroPrices: Boolean) {
        helper.runSerially {
            currentUpgradeFlow?.update { it + Pair("has_intro_price", hasIntroPrices.toTelemetry()) }
            currentDimensions?.let {
                event(eventData("upsell_price_display", it))
                eventExperiments("upsell_price_display", it)
            }
        }
    }

    fun onUpgradeAttempt(flowType: UpgradeFlowType, planId: String?, billingCycle: Int?) {
        helper.runSerially {
            currentDimensions?.let { currentDimensions ->
                val dimensions = currentDimensions + buildMap {
                    put("upgraded_user_plan", planId ?: NO_VALUE)
                    put("billing_cycle", billingCycle?.toString() ?: NO_VALUE)
                }
                event(eventData("upsell_upgrade_attempt", dimensions.withFlowType(flowType)))
                eventExperiments("upsell_upgrade_attempt", dimensions)
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
                eventExperiments("upsell_success", dimensions)
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
    ): Map<String, String> =
        createBasicUpsellDimensions() + buildMap {
            put("modal_source", upgradeSource.reportedName)
            put("modal_trigger", upgradeTrigger.reportedName)
            put("new_free_plan_ui", "yes") // Used to be a feature flag.
            put("reference", reference ?: NO_VALUE)

            if (countryId != null && !countryId.isFastest) {
                put("country", countryId.countryCode)
            }
        }

    private suspend fun createBasicUpsellDimensions(): Map<String, String> = buildMap {
        val user = currentUser.user()
        val vpnUser = currentUser.vpnUser()

        commonDimensions.add(
            this,
            CommonDimensions.Key.USER_COUNTRY_LEGACY,
            CommonDimensions.Key.VPN_STATUS_LEGACY,
            CommonDimensions.Key.USER_TIER_LEGACY,
            CommonDimensions.Key.IS_CREDENTIAL_LESS_ENABLED_LEGACY
        )
        if (user != null && vpnUser != null) {
            val timeSinceCreation = (clock() - user.createdAtUtc).milliseconds.takeIf { user.createdAtUtc > 0 }
            put("days_since_account_creation", accountCreationBucket(timeSinceCreation))
            put("user_plan", vpnUser.planName ?: NO_VALUE)
        }
    }

    private suspend fun TelemetryFlowHelper.RunSeriallyScope.eventExperiments(
        eventName: String,
        dimensions: Map<String, String>,
    ) {
        eventExperiment12m(eventName, dimensions)
        eventExperimentComparisonTable(eventName, dimensions)
    }

    private suspend fun TelemetryFlowHelper.RunSeriallyScope.eventExperiment12m(
        eventName: String,
        dimensions: Map<String, String>,
    ) {
        val group12m = ifOrNull(isEligibleFor12mExperiment == true) {
            currentUser.vpnUser()?.let { AbTest12mPromo.fromUserId(it.userId) }
        }
        if (group12m != null) {
            val experimentVariant: Pair<String, String> =
                "experiment_variant" to group12m.reportedValue
            event(
                measurementGroup = EXPERIMENT_12M_MEASUREMENT_GROUP,
                event = eventName,
                dimensions = dimensions + experimentVariant
            )
        }
    }

    private suspend fun TelemetryFlowHelper.RunSeriallyScope.eventExperimentComparisonTable(
        eventName: String,
        dimensions: Map<String, String>,
    ) {
        val groupComparisonTable = ifOrNull(isComparisonTableExperiment == true) {
            currentUser.vpnUser()?.let { AbTestComparisonTable.fromUserId(it.userId) }
        }
        if (groupComparisonTable != null) {
            val experimentVariant: Pair<String, String> = "experiment_variant" to groupComparisonTable.reportedValue
            event(
                measurementGroup = EXPERIMENT_COMPARISON_TABLE_MEASUREMENT_GROUP,
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
        private const val EXPERIMENT_COMPARISON_TABLE_MEASUREMENT_GROUP =
            "vpn.any.experiment_upsell_comparison_table_202607"
        private val UPGRADE_FLOW_VALID_MS = TimeUnit.MINUTES.toMillis(10)
    }
}
