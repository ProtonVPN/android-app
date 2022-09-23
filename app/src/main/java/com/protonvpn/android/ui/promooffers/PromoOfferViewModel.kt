/*
 * Copyright (c) 2021. Proton Technologies AG
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
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationOfferPanel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PromoOfferViewModel @Inject constructor(
    private val apiNotificationManager: ApiNotificationManager,
    private val promoOfferButtonActions: PromoOfferButtonActions
): ViewModel() {

    val openUrlEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val isLoading = MutableStateFlow(false)

    private lateinit var currentPanel: ApiNotificationOfferPanel

    suspend fun init(offerId: String): ApiNotificationOfferPanel? {
        val offerPanel = apiNotificationManager.activeListFlow
            .firstOrNull()
            ?.find { it.id == offerId }
            ?.offer
            ?.panel
        if (offerPanel != null) currentPanel = offerPanel
        return offerPanel
    }

    fun onOpenOfferClicked() {
        val button = currentPanel.button ?: return

        viewModelScope.launch {
            isLoading.value = true
            val urlToOpen = promoOfferButtonActions.getButtonUrl(button)
            isLoading.value = false
            if (urlToOpen != null)
                openUrlEvent.tryEmit(urlToOpen)
        }
    }
}
