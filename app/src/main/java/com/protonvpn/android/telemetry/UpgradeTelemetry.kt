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

import com.protonvpn.android.appconfig.GetFeatureFlags
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.telemetry.CommonDimensions.Companion.NO_VALUE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class UpgradeSource {
    ALLOW_LAN,
    CHANGE_SERVER,
    COUNTRIES,
    DOWNGRADE,
    MAX_CONNECTIONS,
    MODERATE_NAT,
    NETSHIELD,
    P2P,
    PORT_FORWARDING,
    PROFILES,
    SAFE_MODE,
    SECURE_CORE,
    SPLIT_TUNNELING,
    STREAMING,
    VPN_ACCELERATOR,
    PROMO_OFFER;

    val reportedName = name.lowercase()
}

@Singleton
class UpgradeTelemetry @Inject constructor(
    private val mainScope: CoroutineScope,
    private val commonDimensions: CommonDimensions,
    private val currentUser: CurrentUser,
    private val getFeatureFlags: GetFeatureFlags,
    private val telemetry: Telemetry,
    @WallClock private val clock: () -> Long
) {

    private var currentUpsellDimensions: Map<String, String>? = null

    fun onUpgradeFlowStarted(upgradeSource: UpgradeSource, reference: String? = null) {
        mainScope.launch {
            val dimensions = createDimensions(upgradeSource, reference)
            currentUpsellDimensions = dimensions
            sendEvent("upsell_display", dimensions)
        }
    }

    fun onUpgradeAttempt() {
        currentUpsellDimensions?.let { currentDimensions ->
            sendEvent("upsell_upgrade_attempt", currentDimensions)
        }
    }

    fun onUpgradeSuccess(newPlanId: String?) {
        currentUpsellDimensions?.let { currentDimensions ->
            val upgradedPlan = newPlanId ?: NO_VALUE
            val dimensions = currentDimensions + ("upgraded_user_plan" to upgradedPlan)
            currentUpsellDimensions = null
            sendEvent("upsell_success", dimensions)
        }
    }

    private fun sendEvent(eventName: String, dimensions: Map<String, String>) {
        telemetry.event(MEASUREMENT_GROUP, eventName, emptyMap(), dimensions)
    }

    private suspend fun createDimensions(
        upgradeSource: UpgradeSource,
        reference: String?
    ): Map<String, String> =
        buildMap {
        val user = currentUser.user()
        val vpnUser = currentUser.vpnUser()

        commonDimensions.add(this, CommonDimensions.Key.USER_COUNTRY, CommonDimensions.Key.VPN_STATUS)
        put("modal_source", upgradeSource.reportedName)
        put("new_free_plan_ui", if (getFeatureFlags.value.showNewFreePlan) "yes" else "no")
        put("reference", reference ?: NO_VALUE)

        if (user != null && vpnUser != null) {
            val timeSinceCreation = (clock() - user.createdAtUtc).milliseconds.takeIf { user.createdAtUtc > 0 }
            put("days_since_account_creation", accountCreationBucket(timeSinceCreation))
            put("user_plan", vpnUser.planName ?: NO_VALUE)
        }
    }

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

    companion object {
        private const val MEASUREMENT_GROUP = "vpn.any.upsell"
    }
}
