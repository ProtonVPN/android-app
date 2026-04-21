/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.ui.planupgrade.comparison_table

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ui.label
import com.protonvpn.android.utils.CountryTools
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun UpsellCountryTablePanel(
    country: CountryId?,
    freeCountries: Int,
    plusCountries: Int,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.systemBars
) {
    val image = remember(country) {
        if (country != null) {
            @Composable { SparklingFlag(country) }
        } else {
            @Composable {
                Image(
                    painterResource(R.drawable.upsell_header_countries),
                    contentDescription = null,
                )
            }
        }
    }
    val title = if (country != null) {
        stringResource(R.string.upsell_panel_country_title, country.label())
    } else {
        stringResource(R.string.upsell_panel_countries_title)
    }
    UpsellComparisonTablePanel(
        title = title,
        image = image,
        windowInsets = windowInsets,
        modifier = modifier
    ) {
        val rowModifier = Modifier.fillMaxWidth()
        Column {
            BenefitTableFreePlusHeader(
                modifier = rowModifier,
            )
            val plusCountriesRounded = (plusCountries / 10) * 10
            BenefitTableRow(
                stringResource(R.string.upsell_panel_country_benefit_countries),
                firstPlanContent = {
                    Text("%s".format(freeCountries))
                },
                secondPlanContent = {
                    Text(stringResource(R.string.upsell_panel_country_benefit_countries_rounded, plusCountriesRounded))
                },
                modifier = rowModifier,
            )
            BenefitTableRowNoYes(
                stringResource(R.string.upsell_panel_country_benefit_choice),
                modifier = rowModifier,
            )
            BenefitTableRowNoYes(
                stringResource(R.string.upsell_panel_country_benefit_speed),
                modifier = rowModifier,
            )
            BenefitTableRowNoYes(
                stringResource(R.string.upsell_panel_country_benefit_shows),
                modifier = rowModifier,
                secondPlanBackgroundShape = BenefitTableRowDefaults.ShapeBottom,
                bottomSeparator = false,
            )
        }
    }
}

@Composable
private fun SparklingFlag(
    country: CountryId,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val flagResource = if (LocalInspectionMode.current) {
        R.drawable.flag_large_ad
    } else {
        CountryTools.getFlagResource(context, country.countryCode)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Image(
            painterResource(R.drawable.upgrade_country_flag_backgroud),
            contentDescription = null,
        )
        Image(
            painterResource(flagResource),
            contentDescription = null,
            contentScale = ContentScale.FillWidth, // Flag images are square with padding.
            modifier = Modifier
                .clip(ProtonTheme.shapes.small)
                .size(48.dp, 32.dp)
        )
    }
}