/*
 * Copyright (c) 2024 Proton AG
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
package com.protonvpn.android.profiles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.redesign.CountryId
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun CreateProfileTypeAndLocationRoute(
    viewModel: CreateEditProfileViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val state = viewModel.typeAndLocationScreenStateFlow.collectAsStateWithLifecycle(null).value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = ProtonTheme.colors.backgroundNorm)
    ) {
        if (state != null) {
            ProfileTypeAndLocation(
                state = state,
                setType = viewModel::setType,
                setCountry = viewModel::setCountry,
                setCityOrState = viewModel::setCityOrState,
                setServer = viewModel::setServer,
                setExitCountrySecureCore = viewModel::setExitCountrySecureCore,
                setEntryCountrySecureCore = viewModel::setEntryCountrySecureCore,
                setGateway = viewModel::setGateway,
                onNext = { onNext() },
                onBack = { onBack() },
            )
        }
    }
}

@Composable
fun ProfileTypeAndLocation(
    state: TypeAndLocationScreenState,
    setType: (ProfileType) -> Unit,
    setCountry: (TypeAndLocationScreenState.CountryItem) -> Unit,
    setCityOrState: (TypeAndLocationScreenState.CityOrStateItem) -> Unit,
    setServer: (TypeAndLocationScreenState.ServerItem) -> Unit,
    setExitCountrySecureCore: (TypeAndLocationScreenState.CountryItem) -> Unit,
    setEntryCountrySecureCore: (TypeAndLocationScreenState.CountryItem) -> Unit,
    setGateway: (TypeAndLocationScreenState.GatewayItem) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    CreateProfileStep(
        onNext = onNext,
        onBack = onBack,
    ) {
        Text(
            text = stringResource(id = R.string.create_profile_type_and_location_title),
            color = ProtonTheme.colors.textNorm,
            style = ProtonTheme.typography.body1Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        ProfileTypeItem(state.type, state.availableTypes, setType, modifier = Modifier.testTag("profileTypeDropdown"))
        when (state) {
            is TypeAndLocationScreenState.P2P,
            is TypeAndLocationScreenState.Standard -> {
                ProfileCountryItem(
                    secureCore = false,
                    exitCountry = state.country,
                    entryCountry = null,
                    state.selectableCountries,
                    emptyList(),
                    onSelectExit = setCountry,
                    onSelectEntry = {},
                    modifier = Modifier.testTag("profileCountryDropdown")
                )
                val cityOrState = state.cityOrState
                if (cityOrState != null) {
                    ProfileCityOrStateItem(cityOrState, state.selectableCitiesOrStates, setCityOrState)
                    val server = state.server
                    if (server != null) {
                        ProfileServerItem(server, state.selectableServers, setServer)
                    }
                }
            }
            is TypeAndLocationScreenState.SecureCore -> {
                ProfileCountryItem(
                    secureCore = true,
                    exitCountry = state.exitCountry,
                    entryCountry = state.entryCountry,
                    state.selectableExitCountries,
                    state.selectableEntryCountries,
                    onSelectExit = setExitCountrySecureCore,
                    onSelectEntry = setEntryCountrySecureCore,
                    modifier = Modifier.testTag("profileCountryDropdown")
                )
            }
            is TypeAndLocationScreenState.Gateway -> {
                ProfileGatewayItem(state.gateway, state.selectableGateways, setGateway)
                ProfileServerItem(state.server, state.selectableServers, setServer)
            }
        }
    }
}

@Preview
@Composable
fun PreviewProfileTypeAndLocation() {
   ProfileTypeAndLocation(
        onNext = {},
        onBack = {},
        state = TypeAndLocationScreenState.Standard(emptyList(), TypeAndLocationScreenState.CountryItem(CountryId.fastest, true), null, null, emptyList(), emptyList(), emptyList()),
        setType = {},
        setCountry = {},
        setCityOrState = {},
        setServer = {},
        setExitCountrySecureCore = {},
        setEntryCountrySecureCore = {},
        setGateway = {},
   )
}
