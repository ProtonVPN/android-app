/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.redesign.vpn.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.LocalLocale
import com.protonvpn.android.base.ui.glanceAwareStringResource
import com.protonvpn.android.base.ui.replaceWithInlineContent
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.utils.CountryTools

const val FREE_USER_FLAGS_PLACEHOLDER = "[flags]"

@Composable
fun ConnectIntentPrimaryLabel.label(): String = when (this) {
    is ConnectIntentPrimaryLabel.Fastest ->
        glanceAwareStringResource(if (isFree) R.string.fastest_free_server else R.string.fastest_country)
    is ConnectIntentPrimaryLabel.Country -> exitCountry.label()
    is ConnectIntentPrimaryLabel.Gateway -> gatewayName
    is ConnectIntentPrimaryLabel.Profile -> name
}

@Composable
fun ConnectIntentSecondaryLabel.label(plainText: Boolean = false): AnnotatedString = when (this) {
    is ConnectIntentSecondaryLabel.RawText -> AnnotatedString(text)
    is ConnectIntentSecondaryLabel.Country -> {
        val suffix = serverNumberLabel?.let { " $it" } ?: ""
        AnnotatedString(country.label() + suffix)
    }
    is ConnectIntentSecondaryLabel.SecureCore -> when {
        entry.isFastest -> AnnotatedString(glanceAwareStringResource(R.string.connection_info_secure_core_entry_fastest))
        exit != null -> AnnotatedString(viaCountry(exit, entry))
        else -> AnnotatedString(viaCountry(entry))
    }
    is ConnectIntentSecondaryLabel.FastestFreeServer -> {
        if (plainText) {
            AnnotatedString(glanceAwareStringResource(R.string.connection_info_auto_selected_free_countries_plain))
        } else {
            val text = if (freeServerCountries > 3)
                glanceAwareStringResource(
                    R.string.connection_info_auto_selected_free_countries_more,
                    freeServerCountries - 3
                )
            else
                glanceAwareStringResource(R.string.connection_info_auto_selected_free_countries)
            text.replaceWithInlineContent(FREE_USER_FLAGS_PLACEHOLDER, FREE_USER_FLAGS_PLACEHOLDER)
        }
    }
}

@Composable
fun ConnectIntentSecondaryLabel.contentDescription(): String? = when (this) {
    is ConnectIntentSecondaryLabel.FastestFreeServer ->
        pluralStringResource(
            R.plurals.connection_info_accessibility_auto_selected_free_countries,
            freeServerCountries,
            freeServerCountries
        )
    else -> null
}

@Composable
fun CountryId.label(): String =
    when(this) {
        CountryId.Companion.fastestExcludingMyCountry -> glanceAwareStringResource(R.string.fastest_country_excluding_my_country)
        CountryId.Companion.fastest -> glanceAwareStringResource(R.string.fastest_country)
        else -> CountryTools.getFullName(LocalLocale.current, countryCode)
    }

@Composable
fun viaCountry(entryCountry: CountryId): String =
    when (entryCountry) {
        CountryId.Companion.iceland -> glanceAwareStringResource(R.string.connection_info_secure_core_entry_iceland)
        CountryId.Companion.sweden -> glanceAwareStringResource(R.string.connection_info_secure_core_entry_sweden)
        CountryId.Companion.switzerland -> glanceAwareStringResource(R.string.connection_info_secure_core_entry_switzerland)
        else -> glanceAwareStringResource(
            R.string.connection_info_secure_core_entry_other,
            entryCountry.label()
        )
    }

@Composable
private fun viaCountry(exitCountry: CountryId, entryCountry: CountryId): String =
    when (entryCountry) {
        CountryId.Companion.iceland -> glanceAwareStringResource(
            R.string.connection_info_secure_core_full_iceland,
            exitCountry.label()
        )
        CountryId.Companion.sweden -> glanceAwareStringResource(
            R.string.connection_info_secure_core_full_sweden,
            exitCountry.label()
        )
        CountryId.Companion.switzerland ->
            glanceAwareStringResource(
                R.string.connection_info_secure_core_full_switzerland,
                exitCountry.label()
            )
        else ->
            glanceAwareStringResource(
                R.string.connection_info_secure_core_full_other,
                exitCountry.label(),
                entryCountry.label()
            )
    }