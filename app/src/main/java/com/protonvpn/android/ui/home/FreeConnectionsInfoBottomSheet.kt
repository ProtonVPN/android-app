/*
 * Copyright (c) 2023 Proton Technologies AG
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
package com.protonvpn.android.ui.home

import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.hilt.navigation.compose.hiltViewModel
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.SimpleModalBottomSheet
import com.protonvpn.android.databinding.FreeConnectionsInfoBinding
import com.protonvpn.android.databinding.InfoFreeCountryItemBinding
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.ui.planupgrade.UpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesHighlightsFragment
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ViewUtils.toPx
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.subheadlineNorm
import me.proton.core.presentation.utils.onClick

private const val FLAG_WIDTH_DP = 24
private const val FLAG_HEIGHT_DP = 16

@Composable
fun FreeConnectionsInfoBottomSheet(
    onDismissRequest: () -> Unit,
    viewModel: FreeConnectionsInfoViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.reportUpgradeFlowStart(UpgradeSource.COUNTRIES)
    }
    FreeConnectionsInfoBottomSheet(onDismissRequest, viewModel.freeCountriesCodes)
}
@Composable
private fun FreeConnectionsInfoBottomSheet(
    onDismissRequest: () -> Unit,
    freeCountries: List<String>,
) {
    SimpleModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.free_connection_info_title),
                style = ProtonTheme.typography.subheadlineNorm,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            val context = LocalContext.current
            AndroidViewBinding(FreeConnectionsInfoBinding::inflate) {
                setupViews(context, freeCountries)
            }
        }
    }
}

private fun FreeConnectionsInfoBinding.setupViews(context: Context, freeCountries: List<String>) {
    locationsHeader.text =
        context.getString(R.string.free_connections_info_server_locations, freeCountries.size)
    upsellBanner.textTitle.setText(R.string.free_connections_info_banner_text)
    upsellBanner.root.onClick {
        UpgradeDialogActivity.launch<UpgradePlusCountriesHighlightsFragment>(context)
    }
    for (country in freeCountries) {
        val item = InfoFreeCountryItemBinding.inflate(
            LayoutInflater.from(context), freeCountriesContainer, true)
        item.countryName.text = CountryTools.getFullName(country)
        item.imageFlag.setImageResource(
            CountryTools.getFlagResource(context, country))
        item.imageFlag.outlineProvider = object: ViewOutlineProvider() {
            override fun getOutline(view: View?, outline: android.graphics.Outline?) {
                val dyDp = (FLAG_WIDTH_DP - FLAG_HEIGHT_DP) / 2
                outline?.setRoundRect(
                    Rect(0, dyDp.toPx(), FLAG_WIDTH_DP.toPx(), (FLAG_WIDTH_DP - dyDp).toPx()),
                    4.toPx().toFloat()
                )
            }
        }
        item.imageFlag.clipToOutline = true
    }
}
