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

package com.protonvpn.android.tests.base.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ConnectIntentIcon
import com.protonvpn.android.redesign.base.ui.ConnectIntentIconSize
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ConnectIntentIconMediumDark() {
    ProtonVpnPreview {
        ConnectIntentIcons(ConnectIntentIconSize.MEDIUM)
    }
}

@Preview(locale = "fa")
@Composable
fun ConnectIntentIconMediumRtlLight() {
    ProtonVpnPreview {
        ConnectIntentIcons(ConnectIntentIconSize.MEDIUM)
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ConnectIntentIconSmallDark() {
    ProtonVpnPreview {
        ConnectIntentIcons(ConnectIntentIconSize.SMALL)
    }
}

@Preview(locale = "fa")
@Composable
fun ConnectIntentIconSmallRtlLight() {
    ProtonVpnPreview {
        ConnectIntentIcons(ConnectIntentIconSize.SMALL)
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ConnectIntentIconLargeDark() {
    ProtonVpnPreview {
        ConnectIntentIcons(ConnectIntentIconSize.LARGE)
    }
}

@Preview(locale = "fa")
@Composable
fun ConnectIntentIconLargeRtlLight() {
    ProtonVpnPreview {
        ConnectIntentIcons(ConnectIntentIconSize.LARGE)
    }
}

@Composable
private fun ConnectIntentIcons(
    size: ConnectIntentIconSize,
    modifier: Modifier = Modifier
) {
    val labels = listOf(
        ConnectIntentPrimaryLabel.Fastest(null, false, true),
        ConnectIntentPrimaryLabel.Fastest(CountryId.sweden, false, true),
        ConnectIntentPrimaryLabel.Fastest(CountryId.sweden, true, false),
        ConnectIntentPrimaryLabel.Country(CountryId.iceland, null),
        ConnectIntentPrimaryLabel.Country(CountryId.iceland, CountryId.switzerland),
        ConnectIntentPrimaryLabel.Gateway("Gateway", null),
        ConnectIntentPrimaryLabel.Gateway("Gateway", CountryId.switzerland),
        ConnectIntentPrimaryLabel.Profile("Profile", CountryId.fastest, false, ProfileIcon.Icon1, ProfileColor.Color1),
        ConnectIntentPrimaryLabel.Profile("Profile", CountryId.fastest, true, ProfileIcon.Icon5, ProfileColor.Color1),
        ConnectIntentPrimaryLabel.Profile("Profile", CountryId.iceland, true, ProfileIcon.Icon5, ProfileColor.Color3),
        ConnectIntentPrimaryLabel.Profile("Profile", CountryId.iceland, false, ProfileIcon.Icon7, ProfileColor.Color6),
    )

    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEach { label ->
            ConnectIntentIcon(label, Modifier, size)
        }
    }
}
