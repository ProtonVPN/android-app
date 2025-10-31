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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.SimpleTopAppBar
import com.protonvpn.android.base.ui.TopAppBarCloseIcon
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.VpnWeakSolidButton
import com.protonvpn.android.profiles.ui.nav.ProfileCreationSubscreenTarget
import com.protonvpn.android.profiles.ui.nav.ProfileCreationStepTarget
import com.protonvpn.android.profiles.ui.nav.ProfilesAddEditStepNav
import com.protonvpn.android.profiles.ui.nav.ProfilesRegularAndSubscreenNav
import com.protonvpn.android.redesign.base.ui.ProtonAlert
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.base.ui.preventMultiClick
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.utils.currentLocale

@Composable
fun AddEditProfileRoute(
    profileId: Long? = null,
    duplicate: Boolean = false,
    navigateTo: ProfileCreationStepTarget?,
    onDismiss: () -> Unit,
) {
    val viewModel : CreateEditProfileViewModel = hiltViewModel()

    val locale = LocalConfiguration.current.currentLocale()
    LaunchedEffect(Unit) { viewModel.localeFlow.value = locale }
    viewModel.setEditedProfileId(profileId, duplicate)

    AddEditProfileScreen(
        viewModel,
        onDismiss,
        navigateTo = navigateTo,
        isEditMode = profileId != null && !duplicate,
        onProfileSave = {
            viewModel.saveOrShowReconnectDialog(
                routedFromSettings = navigateTo != null,
                onDismiss = onDismiss
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProfileScreen(
    viewModel: CreateEditProfileViewModel,
    onDismiss: () -> Unit,
    navigateTo: ProfileCreationStepTarget?,
    isEditMode: Boolean = false,
    onProfileSave: () -> Unit,
) {
    val reconnectDialog = viewModel.showReconnectDialogFlow.collectAsStateWithLifecycle().value
    if (reconnectDialog) {
        ReconnectDialog(
            onConfirm = {
                viewModel.saveAndReconnect(
                    routedFromSettings = navigateTo != null
                )
                onDismiss()
            },
            onDismiss = viewModel::dismissReconnectDialog
        )
    }

    val mainNavController = rememberNavController()
    val mainNavigator = remember { ProfilesRegularAndSubscreenNav(mainNavController) }
    mainNavigator.NavHost(
        viewModel = viewModel,
        navigateTo = navigateTo,
        isEditMode = isEditMode,
        onDone = onProfileSave,
        onClose = onDismiss,
    )
}

@Composable
fun AddEditProfileSteps(
    viewModel: CreateEditProfileViewModel,
    navController: NavHostController,
    navigateTo: ProfileCreationStepTarget?,
    isEditMode: Boolean = false,
    onNavigateToSubscreen: (ProfileCreationSubscreenTarget) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = remember { ProfilesAddEditStepNav(navController) }
    val nameState = viewModel.nameScreenStateFlow.collectAsStateWithLifecycle()
    val totalSteps = ProfileCreationStepTarget.entries.size
    Scaffold(
        topBar = {
            SimpleTopAppBar(
                title = {
                    Text(
                        text =
                            if (isEditMode)
                                stringResource(
                                    id = R.string.edit_profile_title,
                                    nameState.value?.name ?: ""
                                )
                            else
                                stringResource(id = R.string.create_profile_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { TopAppBarCloseIcon(onDismiss) }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(modifier = Modifier
            .padding(paddingValues)
            // Workaround for https://issuetracker.google.com/issues/249727298
            .consumeWindowInsets(paddingValues)
        ) {
            StepHeader(navController, totalSteps)

            Spacer(modifier = Modifier.height(16.dp))
            navigator.NavHost(
                viewModel,
                onDone = onDone,
                onNavigateToSubscreen = onNavigateToSubscreen,
                navigateTo = navigateTo,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = largeScreenContentPadding())
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StepHeader(
    navController: NavHostController,
    totalSteps: Int
) {
    Column {
        val currentBackStackEntry = navController.currentBackStackEntryAsState()

        val currentStep = enumValues<ProfileCreationStepTarget>()
            .firstOrNull { it.screen.route == currentBackStackEntry.value?.destination?.route }
            ?.ordinal?.plus(1) ?: 0

        // TODO: this should not be a progress indicator, it doesn't look as in designs.
        LinearProgressIndicator(
            progress = { currentStep / totalSteps.toFloat() },
            modifier = Modifier.fillMaxWidth().semantics { invisibleToUser() },
            trackColor = ProtonTheme.colors.brandDarken40
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(id = R.string.create_profile_steps, currentStep, totalSteps),
            style = ProtonTheme.typography.captionMedium,
            color = ProtonTheme.colors.textAccent,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun CreateProfileStep(
    onNext: () -> Unit,
    onBack: (() -> Unit)? = null,
    onNextText: String = stringResource(id = R.string.create_profile_button_next),
    applyContentHorizontalPadding: Boolean = true,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = if (applyContentHorizontalPadding) 16.dp else 0.dp)
                .verticalScroll(rememberScrollState())
        ) {
            content()
        }

        ProfileNavigationButtons(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            onNext = onNext,
            onBack = onBack,
            onNextText = onNextText,
        )
    }
}

@Composable
private fun ProfileNavigationButtons(
    modifier: Modifier = Modifier,
    onNext: () -> Unit,
    onNextText: String,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        onBack?.let {
            val (backEnabledState, onBackWrapped) = preventMultiClick(action = onBack)
            VpnWeakSolidButton(
                text = stringResource(id = R.string.back),
                onClick = onBackWrapped,
                modifier = Modifier.weight(1f),
                enabled = backEnabledState.value
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        val (nextEnabledState, onNextWrapped) = preventMultiClick(action = onNext)
        VpnSolidButton(
            text = onNextText,
            onClick = onNextWrapped,
            modifier = Modifier.weight(1f),
            enabled = nextEnabledState.value
        )
    }
}

@Composable
fun ReconnectDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ProtonAlert(
        title = null,
        text = stringResource(R.string.profile_needs_reconnect),
        confirmLabel = stringResource(id = R.string.reconnect_now),
        isWideDialog = true,
        onConfirm = { onConfirm() },
        dismissLabel = stringResource(id = R.string.cancel),
        onDismissRequest = onDismiss,
    )
}
