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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.InfoType
import com.protonvpn.android.redesign.base.ui.LocalVpnUiDelegate
import com.protonvpn.android.redesign.base.ui.ProtonAlert
import com.protonvpn.android.redesign.base.ui.ProtonSnackbarType
import com.protonvpn.android.redesign.base.ui.rememberInfoSheetState
import com.protonvpn.android.redesign.base.ui.showSnackbar
import com.protonvpn.android.redesign.home_screen.ui.ShowcaseRecents
import com.protonvpn.android.ui.planupgrade.CarouselUpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeProfilesHighlightsFragment
import kotlinx.coroutines.launch

@Composable
fun ProfilesRoute(
    onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
    onNavigateToAddEdit: (profileId: Long?, duplicate: Boolean) -> Unit,
) {
    val viewModel : ProfilesViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val state = viewModel.state.collectAsStateWithLifecycle().value
        // Collect selectedProfile without lifecycle, otherwise changes are not observed during navigation animation to
        // edit profile and the bottom sheet closes with a delay.
        val selectedProfile = viewModel.selectedProfile.collectAsState().value

        val autoShowInfoSheet by viewModel.autoShowInfoSheet.collectAsStateWithLifecycle(false)
        val infoSheetState = rememberInfoSheetState()
        if (autoShowInfoSheet) {
            LaunchedEffect(Unit) {
                infoSheetState.show(InfoType.Profiles)
                viewModel.onAutoShowInfoSheet()
            }
        }
        if (state != null) {
            val context = LocalContext.current
            val uiDelegate = LocalVpnUiDelegate.current
            val navigateToUpsell = { CarouselUpgradeDialogActivity.launch<UpgradeProfilesHighlightsFragment>(context) }
            Profiles(
                state = state,
                onConnect = { profile ->
                    viewModel.onConnect(profile, uiDelegate, onNavigateToHomeOnConnect, navigateToUpsell)
                },
                onSelect = { profile ->
                    viewModel.onSelect(profile)
                },
                onAddNew = { onNavigateToAddEdit(null, false) },
                snackbarHostState = snackbarHostState,
                infoSheetState = infoSheetState,
            )
        }

        val coroutineScope = rememberCoroutineScope()
        if (selectedProfile != null) {
            val snackProfileDeleteMessage = stringResource(R.string.profile_deleted_snackbar_message)
            val snackUndo = stringResource(R.string.undo)
            ProfileBottomSheet(
                profile = selectedProfile,
                onClose = viewModel::onProfileClose,
                onProfileEdit = { onNavigateToAddEdit(it.profile.id, false) },
                onProfileDuplicate = { onNavigateToAddEdit(it.profile.id, true) },
                onProfileDelete = {
                    // SnackbarDuration.Short is 4s, let's use a slightly longer value on the delete operation.
                    val undoOperation = viewModel.onProfileDelete(it, undoDurationMs = 5000L)
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = snackProfileDeleteMessage,
                            actionLabel = snackUndo,
                            duration = SnackbarDuration.Short,
                            type = ProtonSnackbarType.NORM
                        )
                        if (result == SnackbarResult.ActionPerformed)
                            undoOperation()
                    }
                },
            )
        }

        val showDialog = viewModel.showDialog.collectAsStateWithLifecycle().value
        if (showDialog != null)
            ShowProfileDialog(showDialog, viewModel::dismissDialog)
    }
}

@Composable
private fun ShowProfileDialog(
    showDialog: ProfilesViewModel.Dialog,
    onDismiss: () -> Unit,
) {
    when (showDialog) {
        is ProfilesViewModel.Dialog.ServerUnavailable ->
            ProtonAlert(
                title = stringResource(R.string.profile_unavailable_dialog_title),
                text = stringResource(R.string.profile_unavailable_dialog_message, showDialog.profileName),
                confirmLabel = stringResource(R.string.got_it),
                onConfirm = { onDismiss() },
                onDismissRequest = onDismiss
            )
    }
}
