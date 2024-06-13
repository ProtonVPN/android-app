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

package com.protonvpn.android.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun ProtonTvDialogBasic(
    onDismissRequest: () -> Unit,
    content: @Composable (FocusRequester) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
    ) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Surface(
            colors = SurfaceDefaults.colors(
                containerColor = ProtonTheme.colors.backgroundSecondary,
                contentColor = ProtonTheme.colors.textNorm,
            ),
            shape = ProtonTheme.shapes.large,
            modifier = Modifier
                .semantics {
                    dismiss {
                        onDismissRequest()
                        true
                    }
                }
        ) {
            Box(
                modifier = Modifier.padding(24.dp)
            ) {
                content(focusRequester)
            }
        }
    }
}
