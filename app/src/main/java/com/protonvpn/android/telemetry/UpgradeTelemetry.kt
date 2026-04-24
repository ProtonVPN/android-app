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
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.telemetry.CommonDimensions.Companion.NO_VALUE
import com.protonvpn.android.ui.planupgrade.UpgradeFlowType
import com.protonvpn.android.utils.getValue
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

enum class UpgradeAbTest(val reportedValue: String) {
    CONTROL("control"), COMPARISON_TABLE("comparison_table")
}

@Singleton
class UpgradeTelemetry @Inject constructor(
    private val commonDimensions: CommonDimensions,
    private val currentUser: CurrentUser,
    @WallClock private val clock: () -> Long,
    telemetryHelperLazy: dagger.Lazy<TelemetryFlowHelper>,
) {
    private val helper by telemetryHelperLazy

    private var currentUpgradeFlow: UpgradeFlow? = null
    private val currentDimensions get() = currentUpgradeFlow?.getCurrentDimensions()

    fun onUpgradeFlowStarted(
        upgradeSource: UpgradeSource,
        upgradeTrigger: UpgradeTrigger,
        abTestGroup: UpgradeAbTest?,
        countryId: CountryId? = null,
        reference: String? = null
    ) {
        helper.event {
            val dimensions =
                createDimensions(upgradeSource, upgradeTrigger, countryId, reference, abTestGroup)
            currentUpgradeFlow = UpgradeFlow(dimensions, clock)
            eventData("upsell_display", dimensions)
        }
    }

    fun onUpgradeAttempt(flowType: UpgradeFlowType) {
        helper.event {
            currentDimensions?.let { currentDimensions ->
                eventData("upsell_upgrade_attempt", currentDimensions.withFlowType(flowType))
            }
        }
    }

    fun onUpgradeSuccess(newPlanId: String?, flowType: UpgradeFlowType, billingCycle: Int) {
        helper.event {
            currentDimensions?.let { currentDimensions ->
                val upgradedPlan = newPlanId ?: NO_VALUE
                val dimensions = currentDimensions + mapOf(
                    "upgraded_user_plan" to upgradedPlan,
                    "billing_cycle" to billingCycle.toString(),
                )
                currentUpgradeFlow = null
                eventData("upsell_success", dimensions.withFlowType(flowType))
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
        abTestGroup: UpgradeAbTest?,
    ): Map<String, String> = buildMap {
        val user = currentUser.user()
        val vpnUser = currentUser.vpnUser()

        commonDimensions.add(this, CommonDimensions.Key.USER_COUNTRY_LEGACY, CommonDimensions.Key.VPN_STATUS_LEGACY,
            CommonDimensions.Key.USER_TIER_LEGACY, CommonDimensions.Key.IS_CREDENTIAL_LESS_ENABLED_LEGACY)
        put("modal_source", upgradeSource.reportedName)
        put("modal_trigger", upgradeTrigger.reportedName)
        put("new_free_plan_ui", "yes") // Used to be a feature flag.
        put("reference", reference ?: NO_VALUE)
        put("vpn_upsell_modal_comparison_table_20260427", abTestGroup?.reportedValue ?: NO_VALUE)

        if (countryId != null && !countryId.isFastest) {
            put("country", countryId.countryCode)
        }

        if (user != null && vpnUser != null) {
            val timeSinceCreation = (clock() - user.createdAtUtc).milliseconds.takeIf { user.createdAtUtc > 0 }
            put("days_since_account_creation", accountCreationBucket(timeSinceCreation))
            put("user_plan", vpnUser.planName ?: NO_VALUE)
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
        private val dimensions: Map<String, String>,
        @WallClock private val clock: () -> Long
    ) {
        private val timestamp = clock()

        fun getCurrentDimensions() = dimensions.takeIf { timestamp + UPGRADE_FLOW_VALID_MS >= clock() }
    }

    companion object {
        private const val MEASUREMENT_GROUP = "vpn.any.upsell"
        private val UPGRADE_FLOW_VALID_MS = TimeUnit.MINUTES.toMillis(10)
    }
}
