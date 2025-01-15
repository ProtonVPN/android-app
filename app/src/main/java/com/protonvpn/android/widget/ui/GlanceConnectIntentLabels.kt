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

package com.protonvpn.android.widget.ui

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.Visibility
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.text.Text
import androidx.glance.visibility
import com.protonvpn.android.R
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.utils.CountryTools
import java.util.Locale

@Composable
fun ConnectIntentGlanceLabels(state: ConnectIntentViewState, forceMaxHeight: Boolean, modifier: GlanceModifier = GlanceModifier) {
    Box(modifier) {
        Column {
            Text(
                state.primaryLabel.glanceLabel(),
                maxLines = if (state.secondaryLabel == null) 2 else 1,
                style = ProtonGlanceTheme.typography.defaultNorm,
            )
            state.secondaryLabel?.let { secondaryLabel ->
                Text(
                    secondaryLabel.glanceLabel(),
                    maxLines = 1,
                    style = ProtonGlanceTheme.typography.secondary,
                )
            }
        }
        // Invisible view forcing fixed max height of 2 lines of default text size.
        if (forceMaxHeight) {
            Text(
                text = "\n",
                style = ProtonGlanceTheme.typography.defaultNorm,
                maxLines = 2,
                modifier = GlanceModifier.visibility(Visibility.Invisible)
            )
        }
    }
}

//TODO: reuse existing functions (with some smart stringResource impl)
@Composable
private fun ConnectIntentPrimaryLabel.glanceLabel(): String = when (this) {
    is ConnectIntentPrimaryLabel.Fastest ->
        glanceStringResource(if (isFree) R.string.fastest_free_server else R.string.fastest_country)
    is ConnectIntentPrimaryLabel.Country -> exitCountry.glanceLabel()
    is ConnectIntentPrimaryLabel.Gateway -> gatewayName
    is ConnectIntentPrimaryLabel.Profile -> name
}

@Composable
private fun ConnectIntentSecondaryLabel.glanceLabel(): String = when (this) {
    is ConnectIntentSecondaryLabel.RawText -> text
    is ConnectIntentSecondaryLabel.Country -> {
        val suffix = serverNumberLabel?.let { " $it" } ?: ""
        country.glanceLabel() + suffix
    }

    is ConnectIntentSecondaryLabel.SecureCore -> when {
        entry.isFastest -> glanceStringResource(R.string.connection_info_secure_core_entry_fastest)
        exit != null -> glanceViaCountry(exit, entry)
        else -> glanceViaCountry(entry)
    }

    is ConnectIntentSecondaryLabel.FastestFreeServer ->
        glanceStringResource(R.string.widget_connection_secondary_label_auto_selected)
}

@Composable
fun glanceViaCountry(entryCountry: CountryId): String =
    when (entryCountry) {
        CountryId.iceland -> glanceStringResource(R.string.connection_info_secure_core_entry_iceland)
        CountryId.sweden -> glanceStringResource(R.string.connection_info_secure_core_entry_sweden)
        CountryId.switzerland -> glanceStringResource(R.string.connection_info_secure_core_entry_switzerland)
        else -> glanceStringResource(R.string.connection_info_secure_core_entry_other, entryCountry.glanceLabel())
    }


@Composable
fun glanceViaCountry(exitCountry: CountryId, entryCountry: CountryId): String =
    when (entryCountry) {
        CountryId.iceland -> glanceStringResource(R.string.connection_info_secure_core_full_iceland, exitCountry.glanceLabel())
        CountryId.sweden -> glanceStringResource(R.string.connection_info_secure_core_full_sweden, exitCountry.glanceLabel())
        CountryId.switzerland ->
            glanceStringResource(R.string.connection_info_secure_core_full_switzerland, exitCountry.glanceLabel())
        else ->
            glanceStringResource(R.string.connection_info_secure_core_full_other, exitCountry.glanceLabel(), entryCountry.glanceLabel())
    }

@Composable
private fun CountryId.glanceLabel(): String =
    when(this) {
        CountryId.fastestExcludingMyCountry -> glanceStringResource(R.string.fastest_country_excluding_my_country)
        CountryId.fastest -> glanceStringResource(R.string.fastest_country)
        else ->
            // There is no LocalConfiguration, use Locale directly.
            CountryTools.getFullName(Locale.getDefault(), countryCode)
    }