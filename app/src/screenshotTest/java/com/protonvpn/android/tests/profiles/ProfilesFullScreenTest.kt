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

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.protonvpn.android.annotations.ProtonVpnTestPreview
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfileInfo
import com.protonvpn.android.profiles.ui.ProfileViewItem
import com.protonvpn.android.profiles.ui.Profiles
import com.protonvpn.android.profiles.ui.ProfilesState
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewStateProfile
import com.protonvpn.android.vpn.ProtocolSelection
import kotlin.random.Random

@ProtonVpnTestPreview
@Composable
fun FullScreenProfileList() {
    ProtonVpnPreview {
        Profiles(
            state = ProfilesState.ProfilesList(ProfilesData.getProfileList()),
            onConnect = {},
            onSelect = {},
            onAddNew = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}

@ProtonVpnTestPreview
@Composable
fun FullScreenProfileListZeroState() {
    ProtonVpnPreview {
        Profiles(
            state = ProfilesState.ZeroState,
            onConnect = {},
            onSelect = {},
            onAddNew = {},
            snackbarHostState = SnackbarHostState()
        )
    }
}

private object ProfilesData {
    fun getProfileList(): List<ProfileViewItem> {
        val profileList = mutableListOf<ProfileViewItem>()

        profileList.add(
            createProfileViewItem(
                "CAPS LOCK PROFILE VERY LONG",
                CountryId.sweden,
                ConnectIntentSecondaryLabel.Country(CountryId("CH")),
                true
            )
        )
        profileList.add(createProfileViewItem(
            profileName = "WHITE       SPACE",
            exitCountryId = CountryId.fastest,
            isConnected = false
        ))
        profileList.add(createProfileViewItem(
            profileName = "يوم جيد",
            exitCountryId = CountryId.iceland,
            isConnected = false
        ))
        profileList.add(createProfileViewItem(
            profileName = "@#$%^&*()_+=[]{}|;:,.<>!~",
            exitCountryId = CountryId.switzerland,
            isConnected = false
        ))
        return profileList
    }

    fun createProfileViewItem(
        profileName: String,
        exitCountryId: CountryId,
        secondaryLabel: ConnectIntentSecondaryLabel? = null,
        isConnected: Boolean = false
    ): ProfileViewItem {
        return ProfileViewItem(
            profile = ProfileInfo(
                // nosemgrep: proton-use-of-insecure-random-in-dart
                id = Random.nextLong(),
                name = "Profile",
                color = ProfileColor.Color2,
                icon = ProfileIcon.Icon5,
                createdAt = 25151555,
                isUserCreated = true
            ),
            isConnected = isConnected,
            availability = ConnectIntentAvailability.ONLINE,
            intent = ConnectIntentViewStateProfile(
                primaryLabel = ConnectIntentPrimaryLabel.Profile(
                    name = profileName,
                    country = exitCountryId,
                    isGateway = false,
                    icon = ProfileIcon.Icon5,
                    color = ProfileColor.Color3
                ),
                secondaryLabel = secondaryLabel,
                serverFeatures = setOf()
            ),
            netShieldEnabled = true,
            protocol = ProtocolSelection.STEALTH,
            natType = NatType.Strict,
            customDnsEnabled = true,
            lanConnections = true
        )
    }
}
