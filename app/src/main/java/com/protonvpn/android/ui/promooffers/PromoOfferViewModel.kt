/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.android.ui.promooffers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.appconfig.ApiNotificationActions
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationOfferPanel
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.ui.planupgrade.UpgradeFlowType
import com.protonvpn.android.utils.DebugUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PromoOfferViewModel @Inject constructor(
    private val apiNotificationManager: ApiNotificationManager,
    private val promoOfferButtonActions: PromoOfferButtonActions,
    private val upgradeTelemetry: UpgradeTelemetry
): ViewModel() {

    val openUrlEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val isLoading = MutableStateFlow(false)

    private lateinit var currentPanel: ApiNotificationOfferPanel

    suspend fun init(offerId: String): ApiNotificationOfferPanel? {
        val notification = apiNotificationManager.activeListFlow
            .firstOrNull()
            ?.find { it.id == offerId }
        val offerPanel = notification?.offer?.panel
        if (offerPanel != null) currentPanel = offerPanel
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.PROMO_OFFER, notification?.reference)
        return offerPanel
    }

    fun onOpenOfferClicked() {
        val button = currentPanel.button ?: return

        when {
            ApiNotificationActions.isOpenUrl(button.action) -> {
                upgradeTelemetry.onUpgradeAttempt(flowType = UpgradeFlowType.EXTERNAL)
                viewModelScope.launch {
                    isLoading.value = true
                    val urlToOpen = promoOfferButtonActions.getButtonUrl(button)
                    isLoading.value = false
                    if (urlToOpen != null)
                        openUrlEvent.tryEmit(urlToOpen)
                }
            }

            else -> {
                // We could support "InAppPurchase" but it would generate broken telemetry flow (too many events).
                DebugUtils.fail("Action ${button.action} is not supported in one-time popup.")
            }
        }
    }
}
