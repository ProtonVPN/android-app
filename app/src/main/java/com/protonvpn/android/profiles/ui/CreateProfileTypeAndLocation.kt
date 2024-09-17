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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.redesign.CountryId
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun ProfileTypeAndLocationRoute(
    viewModel: CreateEditProfileViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val state = viewModel.typeAndLocationScreenStateFlow.collectAsStateWithLifecycle().value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = ProtonTheme.colors.backgroundNorm)
    ) {
        if (state != null) {
            ProfileTypeAndLocation(
                state = state,
                onChangeType = viewModel::setType,
                onNext = { onNext() },
                onBack = { onBack() },
                getTypes = { ProfileType.entries } //TODO: get from view model
            )
        }
    }
}

@Composable
fun ProfileTypeAndLocation(
    state: TypeAndLocationScreenState,
    getTypes: () -> List<ProfileType>,
    onChangeType: (ProfileType) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .imePadding()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(id = R.string.create_profile_type_and_location_title),
                color = ProtonTheme.colors.textNorm,
                style = ProtonTheme.typography.body1Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            ProfileTypeItem(state.type, getTypes, onChangeType)
        }

        ProfileNavigationButtons(onNext = onNext, onBack = onBack)
    }
}

@Preview
@Composable
fun PreviewProfileTypeAndLocation() {
   ProfileTypeAndLocation(
       onNext = {},
       onBack = {},
       state = TypeAndLocationScreenState.Standard(CountryId.fastest, null, null),
       onChangeType = {},
       getTypes = { ProfileType.entries }
   )
}