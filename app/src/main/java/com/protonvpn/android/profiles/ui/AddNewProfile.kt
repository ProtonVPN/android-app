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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.VpnWeakSolidButton
import com.protonvpn.android.profiles.ui.nav.ProfileCreationTarget
import com.protonvpn.android.profiles.ui.nav.ProfilesAddEditNav
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultStrongNorm
import me.proton.core.presentation.utils.currentLocale

@Composable
fun AddEditProfileRoute(
    profileId: Long? = null,
    onDismiss: () -> Unit,
) {
    val viewModel : CreateEditProfileViewModel = hiltViewModel()

    val locale = LocalConfiguration.current.currentLocale()
    LaunchedEffect(Unit) { viewModel.localeFlow.value = locale }
    viewModel.setEditedProfileId(profileId)

    AddEditProfileScreen(
        viewModel,
        onDismiss,
        onProfileSave = viewModel::save
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProfileScreen(
    viewModel: CreateEditProfileViewModel,
    onDismiss: () -> Unit,
    onProfileSave: () -> Unit,
) {
    val navController = rememberNavController()
    val navigator = remember { ProfilesAddEditNav(navController) }
    val totalSteps = ProfileCreationTarget.entries.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.create_profile_title),
                        style = ProtonTheme.typography.defaultStrongNorm
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(id = me.proton.core.presentation.R.drawable.ic_proton_cross),
                            contentDescription = stringResource(id = R.string.accessibility_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val currentBackStackEntry = navController.currentBackStackEntryAsState()

        val currentStep = enumValues<ProfileCreationTarget>()
            .firstOrNull { it.route == currentBackStackEntry.value?.destination?.route }
            ?.ordinal?.plus(1) ?: 0

        Column(modifier = Modifier.padding(paddingValues)) {
            LinearProgressIndicator(
                progress = { currentStep / totalSteps.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                trackColor = ProtonTheme.colors.brandDarken40
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(id = R.string.create_profile_steps, currentStep, totalSteps),
                style = ProtonTheme.typography.captionMedium,
                color = ProtonTheme.colors.textAccent,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            navigator.NavHost(
                viewModel,
                onDone = {
                    onProfileSave()
                    onDismiss()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ProfileNavigationButtons(
    modifier: Modifier = Modifier,
    onNext: () -> Unit,
    onNextText: String? = null,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        onBack?.let {
            VpnWeakSolidButton(
                text = stringResource(id = R.string.back),
                onClick = it,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        VpnSolidButton(
            text = onNextText ?: stringResource(id = R.string.create_profile_button_next),
            onClick = onNext,
            modifier = Modifier.weight(1f)
        )
    }
}
