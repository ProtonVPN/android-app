/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.tv.settings

import android.content.DialogInterface
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.R
import com.protonvpn.android.tv.dialogs.TvAlertDialog

@Composable
fun TvSettingsReconnectDialog(
    onReconnectNow: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    TvAlertDialog(
        title = stringResource(id = R.string.settings_dialog_reconnect),
        confirmText = stringResource(id = R.string.reconnect_now),
        dismissText = stringResource(id = R.string.ok),
        focusedButton = DialogInterface.BUTTON_POSITIVE,
        onDismissRequest = onDismissRequest,
        onConfirm = onReconnectNow,
    )
}
