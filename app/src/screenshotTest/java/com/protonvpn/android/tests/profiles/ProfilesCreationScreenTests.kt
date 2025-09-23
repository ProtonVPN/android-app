/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.android.tests.profiles

import androidx.compose.runtime.Composable
import com.protonvpn.android.annotations.ProtonVpnTestPreview
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.ui.CreateName
import com.protonvpn.android.profiles.ui.NameScreenState
import com.protonvpn.android.profiles.ui.ProfileFeaturesAndSettings
import com.protonvpn.android.profiles.ui.ProfileType
import com.protonvpn.android.profiles.ui.ProfileTypeAndLocation
import com.protonvpn.android.profiles.ui.SettingsScreenState
import com.protonvpn.android.profiles.ui.TypeAndLocationScreenState
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.settings.data.CustomDnsSettings
import com.protonvpn.android.vpn.ProtocolSelection

@ProtonVpnTestPreview
@Composable
fun ProfileCreateName() {
    ProtonVpnPreview {
        CreateName(
            state = NameScreenState("Test", ProfileColor.Color3, ProfileIcon.Icon10),
            onNext = {},
            setIcon = {},
            setName = {},
            setColor = {}
        )
    }
}

@ProtonVpnTestPreview
@Composable
fun ProfileTypeAndLocationScreen() {
    ProtonVpnPreview {
        ProfileTypeAndLocation(
            state = TypeAndLocationScreenState.Standard(
                availableTypes = listOf(ProfileType.Standard),
                country = TypeAndLocationScreenState.CountryItem(CountryId("LT"), true),
                cityOrState = TypeAndLocationScreenState.CityOrStateItem(
                    "Test",
                    CityStateId("Vilnius", false),
                    true
                ),
                server = null,
                selectableCountries = listOf(
                    TypeAndLocationScreenState.CountryItem(
                        CountryId.fastest,
                        true
                    )
                ),
                selectableCitiesOrStates = listOf(
                    TypeAndLocationScreenState.CityOrStateItem(
                        "Vilnius",
                        CityStateId("Vilnius", false),
                        true
                    )
                ),
                selectableServers = listOf()
            ),
            onNext = {},
            onBack = {},
            setType = {},
            setServer = {},
            setCountry = {},
            setGateway = {},
            setCityOrState = {},
            setExitCountrySecureCore = {},
            setEntryCountrySecureCore = {}
        )
    }
}


@ProtonVpnTestPreview
@Composable
fun ProfileFeaturesAndSettingsScreen() {
    ProtonVpnPreview {
        ProfileFeaturesAndSettings(
            state = SettingsScreenState(
                netShield = true,
                protocol = ProtocolSelection.SMART,
                natType = NatType.Strict,
                lanConnections = true,
                lanConnectionsAllowDirect = false,
                autoOpen = ProfileAutoOpen.None,
                customDnsSettings = CustomDnsSettings(false),
                isAutoOpenNew = false,
                isPrivateDnsActive = false,
                showPrivateBrowsing = true,
            ),
            onNext = {},
            onBack = {},
            onOpenLan = {},
            onNatChange = {},
            onProtocolChange = {},
            onAutoOpenChange = {},
            onNetShieldChange = {},
            onDisableCustomDns = {},
            onCustomDnsLearnMore = {},
            onOpenCustomDns = {},
            onDisablePrivateDns = {},
            onPrivateDnsLearnMore = {},
            getAutoOpenAppInfo = { null },
            getAutoOpenAllAppsInfo = { emptyList() }
        )
    }
}

@ProtonVpnTestPreview
@Composable
fun ProfileFeaturesAndSettingsScreenCustomDnsConflict() {
    ProtonVpnPreview {
        ProfileFeaturesAndSettings(
            state = SettingsScreenState(
                netShield = true,
                protocol = ProtocolSelection.SMART,
                natType = NatType.Strict,
                lanConnections = true,
                lanConnectionsAllowDirect = false,
                autoOpen = ProfileAutoOpen.None,
                customDnsSettings = CustomDnsSettings(true, listOf("1.1.1.1")),
                isAutoOpenNew = true,
                isPrivateDnsActive = false,
                showPrivateBrowsing = true,
            ),
            onNext = {},
            onBack = {},
            onOpenLan = {},
            onNatChange = {},
            onProtocolChange = {},
            onAutoOpenChange = {},
            onNetShieldChange = {},
            onDisableCustomDns = {},
            onCustomDnsLearnMore = {},
            onOpenCustomDns = {},
            onDisablePrivateDns = {},
            onPrivateDnsLearnMore = {},
            getAutoOpenAppInfo = { null },
            getAutoOpenAllAppsInfo = { emptyList() }
        )
    }
}
