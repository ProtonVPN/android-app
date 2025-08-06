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
import com.protonvpn.android.telemetry.CommonDimensions.Companion.NO_VALUE
import com.protonvpn.android.ui.planupgrade.UpgradeFlowType
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
    DOWNGRADE,
    HOME_CAROUSEL_COUNTRIES,
    HOME_CAROUSEL_CUSTOMIZATION,
    HOME_CAROUSEL_MULTIPLE_DEVICES,
    HOME_CAROUSEL_NETSHIELD,
    HOME_CAROUSEL_P2P,
    HOME_CAROUSEL_PROFILES,
    HOME_CAROUSEL_SECURE_CORE,
    HOME_CAROUSEL_SPEED,
    HOME_CAROUSEL_SPLIT_TUNNELING,
    HOME_CAROUSEL_STREAMING,
    HOME_CAROUSEL_TOR,
    MAX_CONNECTIONS,
    NETSHIELD,
    ONBOARDING,
    P2P,
    TOR,
    PORT_FORWARDING,
    PROFILES,
    SECURE_CORE,
    SPLIT_TUNNELING,
    STREAMING,
    VPN_ACCELERATOR,
    PROMO_OFFER;

    val reportedName = name.lowercase()
}

@Singleton
class UpgradeTelemetry @Inject constructor(
    private val commonDimensions: CommonDimensions,
    private val currentUser: CurrentUser,
    @WallClock private val clock: () -> Long,
    private val helper: TelemetryFlowHelper
) {

    private var currentUpgradeFlow: UpgradeFlow? = null
    private val currentDimensions get() = currentUpgradeFlow?.getCurrentDimensions()

    fun onUpgradeFlowStarted(upgradeSource: UpgradeSource, reference: String? = null) {
        helper.event {
            val dimensions = createDimensions(upgradeSource, reference)
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

    fun onUpgradeSuccess(newPlanId: String?, flowType: UpgradeFlowType) {
        helper.event {
            currentDimensions?.let { currentDimensions ->
                val upgradedPlan = newPlanId ?: NO_VALUE
                val dimensions = currentDimensions + ("upgraded_user_plan" to upgradedPlan)
                currentUpgradeFlow = null
                eventData("upsell_success", dimensions.withFlowType(flowType))
            }
        }
    }

    private fun eventData(eventName: String, dimensions: Map<String, String>) =
        TelemetryEventData(MEASUREMENT_GROUP, eventName, emptyMap(), dimensions)

    private suspend fun createDimensions(
        upgradeSource: UpgradeSource,
        reference: String?
    ): Map<String, String> = buildMap {
        val user = currentUser.user()
        val vpnUser = currentUser.vpnUser()

        commonDimensions.add(this, CommonDimensions.Key.USER_COUNTRY, CommonDimensions.Key.VPN_STATUS,
            CommonDimensions.Key.USER_TIER, CommonDimensions.Key.IS_CREDENTIAL_LESS_ENABLED)
        put("modal_source", upgradeSource.reportedName)
        put("new_free_plan_ui", "yes") // Used to be a feature flag.
        put("reference", reference ?: NO_VALUE)

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
