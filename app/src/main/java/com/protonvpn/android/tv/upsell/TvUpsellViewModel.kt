/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.tv.upsell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.models.features.PaidFeature
import com.protonvpn.android.tv.ui.TvKeyConstants
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TvUpsellViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverListUpdaterPrefs: ServerListUpdaterPrefs,
) : ViewModel() {

    data class ViewState(
        @DrawableRes val imageResId: Int,
        @StringRes val titleResId: Int,
        @StringRes val descriptionResId: Int,
        val descriptionPlaceholders: List<String> = emptyList(),
    ) {

        @StringRes
        val descriptionPlaceholderResId: Int = R.string.upsell_tv_description_placeholder

    }

    val viewStateFlow: StateFlow<ViewState?> = savedStateHandle
        .getStateFlow<PaidFeature?>(
            key = TvKeyConstants.PAID_FEATURE,
            initialValue = null,
        )
        .filterNotNull()
        .mapLatest { paidFeature ->
            when (paidFeature) {
                PaidFeature.AllCountries -> ViewState(
                    imageResId = R.drawable.worldwide_coverage_tv,
                    titleResId = R.string.upsell_tv_all_countries_title,
                    descriptionResId = R.string.upsell_tv_all_countries_description,
                    descriptionPlaceholders = listOf(
                        serverListUpdaterPrefs.vpnServerCount.toString(),
                        serverListUpdaterPrefs.vpnCountryCount.toString(),
                    ),
                )

                PaidFeature.CustomDns,
                PaidFeature.LanConnections -> ViewState(
                    imageResId = R.drawable.customisation_tv,
                    titleResId = R.string.upsell_tv_customization_title,
                    descriptionResId = R.string.upsell_tv_customization_description,
                )

                PaidFeature.NetShield -> ViewState(
                    imageResId = R.drawable.netshield_tv,
                    titleResId = R.string.upsell_tv_netshield_title,
                    descriptionResId = R.string.upsell_tv_netshield_description,
                )

                PaidFeature.SplitTunneling -> ViewState(
                    imageResId = R.drawable.split_tunneling_tv,
                    titleResId = R.string.upsell_tv_split_tunneling_title,
                    descriptionResId = R.string.upsell_tv_split_tunneling_description,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null,
        )

}
