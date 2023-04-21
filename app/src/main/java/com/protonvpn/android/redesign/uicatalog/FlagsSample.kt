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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.Flag
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm

class FlagsSample : SampleScreen("Flags", "flags") {

    private val flagModifier = Modifier.padding(8.dp)

    @Composable
    override fun Content(modifier: Modifier, snackbarHostState: SnackbarHostState) {
        Column(
            modifier.padding(16.dp)
        ) {
            val flagRowModifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
            
            SamplesSectionLabel(label = "Flags for direct connections")
            FlagsRowDirect(flagRowModifier)
            Surface(
                color = ProtonTheme.colors.backgroundSecondary,
                shape = ProtonTheme.shapes.medium
            ) {
                FlagsRowDirect(flagRowModifier)
            }
            VerticalSpacer(height = 16.dp)

            SamplesSectionLabel(label = "Flags for Secure Core")
            FlagsRowSecureCore(flagRowModifier)
            Surface(
                color = ProtonTheme.colors.backgroundSecondary,
                shape = ProtonTheme.shapes.medium
            ) {
                FlagsRowSecureCore(flagRowModifier)
            }
            VerticalSpacer(height = 16.dp)

            SamplesSectionLabel(label = "With text (body-1-regular, top-aligned)")
            FlagWithText("Switzerland", flagRowModifier) { Flag(CountryId("ch")) }
            FlagWithText("Lithuania via Switzerland", flagRowModifier) {
                Flag(CountryId("lt"), CountryId("ch"))
            }
        }
    }

    @Composable
    private fun FlagsRowDirect(modifier: Modifier = Modifier) {
        Row(
            modifier,
            horizontalArrangement = Arrangement.Center
        ) {
            arrayOf(
                CountryId.fastest(),
                CountryId("se"),
                CountryId("pl"),
                CountryId("de"),
                CountryId("se"),
                CountryId("kr"),
                CountryId("au"),
            ).forEach {
                Flag(it, modifier = flagModifier)
            }
        }
    }

    @Composable
    private fun FlagsRowSecureCore(modifier: Modifier = Modifier) {
        Row(
            modifier,
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Center
        ) {
            arrayOf(
                CountryId.fastest() to null,
                CountryId("kr") to null,
                CountryId("au") to null,
                CountryId("pl") to CountryId("se"),
                CountryId("de") to CountryId("se"),
                CountryId("jp") to CountryId("ch"),
                CountryId("ch") to CountryId("ch"),
            ).forEach {
                Flag(it.first, entryCountry = it.second, isSecureCore = true, modifier = flagModifier)
            }
        }
    }

    @Composable
    private fun FlagWithText(label: String, modifier: Modifier = Modifier, flag: @Composable () -> Unit) {
        Row(
            modifier,
            verticalAlignment = Alignment.Top
        ) {
            flag()
            Text(label, style = ProtonTheme.typography.defaultNorm, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}
