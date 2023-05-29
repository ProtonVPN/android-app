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

package com.protonvpn.android.redesign.vpn.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ActiveDot
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.utils.CountryTools
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionUnspecified
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.presentation.utils.currentLocale
import java.util.EnumSet

sealed interface ConnectIntentSecondaryLabel {
    data class Country(val country: CountryId) : ConnectIntentSecondaryLabel
    data class SecureCore(val exit: CountryId?, val entry: CountryId) : ConnectIntentSecondaryLabel
    data class RawText(val text: String) : ConnectIntentSecondaryLabel
}

@Composable
fun ConnectIntentLabels(
    exitCountry: CountryId,
    secondaryLabel: ConnectIntentSecondaryLabel?,
    serverFeatures: Set<ServerFeature>,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        Row {
            Text(
                exitCountry.label(),
                style = ProtonTheme.typography.defaultNorm,
                modifier = Modifier.testTag("primaryLabel")
            )
            if (isConnected) {
                ActiveDot(modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (secondaryLabel != null || serverFeatures.isNotEmpty()) {
            ServerDetailsRow(
                secondaryLabel?.label(),
                serverFeatures,
                modifier = Modifier.testTag("secondaryLabel")
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerDetailsRow(
    detailsText: String?,
    features: Set<ServerFeature>,
    modifier: Modifier = Modifier,
) {
    val bulletPadding = 8.dp
    CompositionLocalProvider(LocalContentColor provides ProtonTheme.colors.textWeak) {
        FlowRow(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            var needsBullet = false
            if (detailsText != null) {
                Text(
                    detailsText,
                    style = ProtonTheme.typography.captionUnspecified,
                    modifier = Modifier.padding(end = bulletPadding)
                )
                needsBullet = true
            }
            features.forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (needsBullet) {
                        SeparatorBullet()
                        Spacer(Modifier.width(bulletPadding))
                    }
                    FeatureTag(feature, Modifier.padding(end = bulletPadding))
                }
                needsBullet = true
            }
        }
    }
}

@Composable
private fun ConnectIntentSecondaryLabel.label() = when (this) {
    is ConnectIntentSecondaryLabel.RawText -> text
    is ConnectIntentSecondaryLabel.Country -> country.label()
    is ConnectIntentSecondaryLabel.SecureCore -> {
        if (exit != null) {
            viaCountry(exit, entry)
        } else {
            viaCountry(entry)
        }
    }
}

@Composable
private fun CountryId.label(): String =
    if (isFastest) {
        stringResource(R.string.fastest_country)
    } else {
        CountryTools.getFullName(LocalConfiguration.current.currentLocale(), countryCode)
    }

@Composable
private fun viaCountry(entryCountry: CountryId): String =
    when (entryCountry) {
        CountryId.iceland -> stringResource(R.string.connection_info_secure_core_entry_iceland)
        CountryId.sweden -> stringResource(R.string.connection_info_secure_core_entry_sweden)
        CountryId.switzerland -> stringResource(R.string.connection_info_secure_core_entry_switzerland)
        else -> stringResource(R.string.connection_info_secure_core_entry_other, entryCountry.label())
    }

@Composable
private fun viaCountry(exitCountry: CountryId, entryCountry: CountryId): String =
    when (entryCountry) {
        CountryId.iceland -> stringResource(R.string.connection_info_secure_core_full_iceland, exitCountry.label())
        CountryId.sweden -> stringResource(R.string.connection_info_secure_core_full_sweden, exitCountry.label())
        CountryId.switzerland ->
            stringResource(R.string.connection_info_secure_core_full_switzerland, exitCountry.label())
        else ->
            stringResource(R.string.connection_info_secure_core_full_other, exitCountry.label(), entryCountry.label())
    }

@Composable
private fun SeparatorBullet(
    modifier: Modifier = Modifier,
) {
    Icon(
        painterResource(id = R.drawable.ic_bullet),
        contentDescription = null,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun ConnectIntentRowPreview() {
    VpnTheme {
        Row {
            ConnectIntentLabels(
                exitCountry = CountryId.fastest,
                secondaryLabel = ConnectIntentSecondaryLabel.RawText("Lithuania"),
                serverFeatures = EnumSet.of(ServerFeature.Tor), isConnected = true
            )
        }
    }
}

@Preview(widthDp = 130)
@Composable
private fun ServerDetailsRowWrappingPreview() {
    VpnTheme {
        Surface(color = ProtonTheme.colors.backgroundSecondary) {
            ServerDetailsRow(detailsText = "Zurich", features = EnumSet.of(ServerFeature.P2P, ServerFeature.Tor))
        }
    }
}
