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

package com.protonvpn.android.redesign.uicatalog

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.protonvpn.android.redesign.main_screen.ui.BottomBarView
import com.protonvpn.android.redesign.main_screen.ui.nav.MainTarget

class BottomBarSample : SampleScreen("Bottom Bar", "bottom bar") {

    @Composable
    override fun Content(modifier: Modifier, snackbarHostState: SnackbarHostState) {
        Box(modifier) {
            val target = rememberSaveable { mutableStateOf(MainTarget.Home) }
            BottomBarView(
                Modifier.align(Alignment.BottomCenter),
                showGateways = true,
                target.value,
            ) {
                target.value = it
            }
        }
    }
}
