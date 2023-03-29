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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.proton.core.compose.component.ProtonOutlinedButton
import me.proton.core.compose.component.ProtonSecondaryButton
import me.proton.core.compose.component.ProtonSolidButton
import me.proton.core.compose.component.ProtonTextButton

class CoreButtonsSample : SampleScreen("Core Buttons", "core_buttons") {

    @Composable
    override fun Content(modifier: Modifier) {
        val topPadding = 16.dp
        val dummyText = "Default button"
        Column(modifier = modifier.padding(16.dp)) {
            ProtonSolidButton(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding)
            ) {
                Text(dummyText)
            }

            ProtonSolidButton(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding)
            ) {
                Text(dummyText)
            }

            ProtonTextButton(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding)
            ) {
                Text(dummyText)
            }

            ProtonOutlinedButton(
                onClick = {}, Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding)
            ) {
                Text(dummyText)
            }

            ProtonOutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding)
            ) {
                Text(dummyText)
            }

            ProtonSolidButton(
                contained = false,
                onClick = {},
                loading = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding)
            ) {
                Text(dummyText)
            }

            ProtonTextButton(
                contained = false,
                onClick = {},
                loading = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding)
            ) {
                Text(dummyText)
            }

            ProtonSecondaryButton(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding)
            ) {
                Text(dummyText)
            }

            ProtonSecondaryButton(
                onClick = {},
                loading = true,
                modifier = Modifier.padding(top = topPadding)
            ) {
                Text(
                    dummyText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
