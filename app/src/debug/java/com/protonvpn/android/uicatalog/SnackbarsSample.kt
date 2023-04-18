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

package com.protonvpn.android.uicatalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.redesign.base.ui.showSnackbar
import com.protonvpn.android.redesign.base.ui.ProtonSnackbarType
import kotlinx.coroutines.launch

class SnackbarsSample : SampleScreen("Snackbars", "snackbars") {

    @Composable
    override fun Content(modifier: Modifier, snackbarHostState: SnackbarHostState) {
        val coroutineScope = rememberCoroutineScope()

        fun showSnackbar(type: ProtonSnackbarType, showAction: Boolean) {
            snackbarHostState.currentSnackbarData?.dismiss()
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Test snackbar", type, "Action".takeIf { showAction })
            }
        }

        var showAction by remember { mutableStateOf(false) }

        Column(modifier = modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = showAction,
                        onValueChange = { showAction = it },
                        role = Role.Checkbox
                    )
                    .padding(vertical = 16.dp)
            ) {
                Text("With action", modifier = Modifier.weight(1f))
                Checkbox(checked = showAction, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
            }
            VpnSolidButton(
                "Show success",
                onClick = { showSnackbar(ProtonSnackbarType.SUCCESS, showAction) }
            )
            VpnSolidButton(
                "Show warning",
                onClick = { showSnackbar(ProtonSnackbarType.WARNING, showAction) }
            )
            VpnSolidButton(
                "Show error",
                onClick = { showSnackbar(ProtonSnackbarType.ERROR, showAction) }
            )
            VpnSolidButton(
                "Show neutral",
                onClick = { showSnackbar(ProtonSnackbarType.NORM, showAction) }
            )
        }
    }
}
