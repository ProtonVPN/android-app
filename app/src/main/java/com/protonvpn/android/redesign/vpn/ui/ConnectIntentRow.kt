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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.replaceWithInlineContent
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ActiveDot
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.utils.CountryTools
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionUnspecified
import me.proton.core.compose.theme.headlineSmallNorm
import me.proton.core.presentation.utils.currentLocale
import java.util.EnumSet

private const val FLAGS_TOKEN = "[flags]"

sealed interface ConnectIntentPrimaryLabel {
    data class Fastest(val connectedCountry: CountryId?, val isSecureCore: Boolean, val isFree: Boolean) : ConnectIntentPrimaryLabel
    data class Country(val exitCountry: CountryId, val entryCountry: CountryId?) : ConnectIntentPrimaryLabel

    data class Gateway(val gatewayName: String, val country: CountryId?) : ConnectIntentPrimaryLabel
}

sealed interface ConnectIntentSecondaryLabel {
    data class FastestFreeServer(val freeServerCountries: Int) : ConnectIntentSecondaryLabel
    data class Country(val country: CountryId, val serverNumberLabel: String? = null) : ConnectIntentSecondaryLabel
    data class SecureCore(val exit: CountryId?, val entry: CountryId) : ConnectIntentSecondaryLabel
    data class RawText(val text: String) : ConnectIntentSecondaryLabel
}

@Composable
fun ConnectIntentLabels(
    primaryLabel: ConnectIntentPrimaryLabel,
    secondaryLabel: ConnectIntentSecondaryLabel?,
    serverFeatures: Set<ServerFeature>,
    isConnected: Boolean,
    secondaryLabelVerticalPadding: Dp,
    primaryLabelStyle: TextStyle = ProtonTheme.typography.headlineSmallNorm,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
    ) {
        Row {
            Text(
                primaryLabel.label(),
                style = primaryLabelStyle,
                modifier = Modifier.testTag("primaryLabel")
            )
            if (isConnected) {
                ActiveDot(modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (secondaryLabel != null || serverFeatures.isNotEmpty()) {
            ServerDetailsRow(
                secondaryLabel?.label(),
                secondaryLabel?.contentDescription(),
                serverFeatures,
                detailsStyle = ProtonTheme.typography.body2Regular,
                modifier = Modifier
                    .padding(top = secondaryLabelVerticalPadding)
                    .testTag("secondaryLabel")
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerDetailsRow(
    detailsText: AnnotatedString?,
    contentDescriptionOverride: String?, // Set to null to use default behavior.
    features: Set<ServerFeature>,
    detailsStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val inlineContent = remember {
        mapOf(
            FLAGS_TOKEN to InlineTextContent(
                // Hardcoded ratio of the dimensions of the flags image...
                Placeholder(3.5f * detailsStyle.fontSize, detailsStyle.fontSize, PlaceholderVerticalAlign.Center)
            ) {
                Image(
                    painterResource(id = R.drawable.free_countries_flags),
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = null
                )
            }
        )
    }
    val bulletPadding = 8.dp
    val semantics = contentDescriptionOverride?.let { Modifier.semantics { contentDescription = it }} ?: Modifier
    CompositionLocalProvider(LocalContentColor provides ProtonTheme.colors.textWeak) {
        FlowRow(
            modifier = modifier.then(semantics),
            verticalArrangement = Arrangement.Center
        ) {
            var needsBullet = false
            if (detailsText != null) {
                Text(
                    detailsText,
                    style = detailsStyle,
                    modifier = Modifier.padding(end = bulletPadding),
                    inlineContent = inlineContent
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
fun ConnectIntentPrimaryLabel.label(): String = when (this) {
    is ConnectIntentPrimaryLabel.Fastest ->
        stringResource(if (isFree) R.string.fastest_free_server else R.string.fastest_country)
    is ConnectIntentPrimaryLabel.Country -> exitCountry.label()
    is ConnectIntentPrimaryLabel.Gateway -> gatewayName
}

@Composable
fun ConnectIntentSecondaryLabel.label(): AnnotatedString = when (this) {
    is ConnectIntentSecondaryLabel.RawText -> AnnotatedString(text)
    is ConnectIntentSecondaryLabel.Country -> {
        val suffix = serverNumberLabel?.let { " $it" } ?: ""
        AnnotatedString(country.label() + suffix)
    }
    is ConnectIntentSecondaryLabel.SecureCore -> when {
        entry.isFastest -> AnnotatedString(stringResource(R.string.connection_info_secure_core_entry_fastest))
        exit != null -> AnnotatedString(viaCountry(exit, entry))
        else -> AnnotatedString(viaCountry(entry))
    }
    is ConnectIntentSecondaryLabel.FastestFreeServer -> {
        val text = if (freeServerCountries > 3)
            stringResource(R.string.connection_info_auto_selected_free_countries_more, freeServerCountries - 3)
        else
            stringResource(R.string.connection_info_auto_selected_free_countries)
        text.replaceWithInlineContent(FLAGS_TOKEN, FLAGS_TOKEN)
    }
}

@Composable
private fun ConnectIntentSecondaryLabel.contentDescription(): String? = when (this) {
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
    if (isFastest) {
        stringResource(R.string.fastest_country)
    } else {
        CountryTools.getFullName(LocalConfiguration.current.currentLocale(), countryCode)
    }

@Composable
fun viaCountry(entryCountry: CountryId): String =
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
private fun ConnectIntentRowPreviewCountry() {
    VpnTheme {
        Row {
            ConnectIntentLabels(
                primaryLabel = ConnectIntentPrimaryLabel.Country(CountryId.fastest, null),
                secondaryLabel = ConnectIntentSecondaryLabel.RawText("Lithuania"),
                serverFeatures = EnumSet.of(ServerFeature.Tor),
                isConnected = true,
                secondaryLabelVerticalPadding = 2.dp,
            )
        }
    }
}

@Preview
@Composable
private fun ConnectIntentRowPreviewGateway() {
    VpnTheme {
        Row {
            ConnectIntentLabels(
                primaryLabel = ConnectIntentPrimaryLabel.Gateway(gatewayName = "Dev VPN", null),
                secondaryLabel = null,
                serverFeatures = EnumSet.of(ServerFeature.Tor),
                isConnected = true,
                secondaryLabelVerticalPadding = 2.dp,
            )
        }
    }
}

@Preview
@Composable
private fun ConnectIntentRowFastestFreeServer() {
    VpnTheme {
        Row {
            ConnectIntentLabels(
                primaryLabel = ConnectIntentPrimaryLabel.Fastest(null, isSecureCore = false, isFree = true),
                secondaryLabel = ConnectIntentSecondaryLabel.FastestFreeServer(5),
                serverFeatures = emptySet(),
                isConnected = true,
                secondaryLabelVerticalPadding = 2.dp,
            )
        }
    }
}

@Preview(widthDp = 130)
@Composable
private fun ServerDetailsRowWrappingPreview() {
    VpnTheme {
        Surface(color = ProtonTheme.colors.backgroundSecondary) {
            ServerDetailsRow(
                detailsText = AnnotatedString("Zurich"),
                contentDescriptionOverride = null,
                features = EnumSet.of(ServerFeature.P2P, ServerFeature.Tor),
                detailsStyle = ProtonTheme.typography.captionUnspecified
            )
        }
    }
}
