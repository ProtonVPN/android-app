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
package com.protonvpn.android.redesign.settings.ui

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.SettingsRadioItemSmall
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultSmallWeak

class TestCrash : RuntimeException("Test exception, everything's fine")

@Composable
fun NatTypeSettings(
    onClose: () -> Unit,
    nat: SettingsViewModel.SettingViewState.Nat,
    onNatTypeChange: (NatType) -> Unit,
) {
    SubSetting(
        title = stringResource(id = nat.titleRes),
        onClose = onClose
    ) {
        Text(
            modifier = Modifier
                .detectMultiTap(7) { throw TestCrash() }
                .padding(16.dp),
            text = stringResource(id = R.string.settings_advanced_nat_type_description_no_learn),
            style = ProtonTheme.typography.defaultSmallWeak,
        )
        NatType.entries.forEach { type ->
            SettingsRadioItemSmall(
                title = stringResource(id = type.labelRes),
                description = stringResource(id = type.descriptionRes),
                selected = type == nat.value,
                onSelected = { onNatTypeChange(type) },
                horizontalContentPadding = 16.dp
            )
        }
    }
}

private fun Modifier.detectMultiTap(count: Int, onTriggered: () -> Unit) =
    this.pointerInput(Unit) {
        var consecutiveTaps = 0
        var lastTapTimestampMs = 0L
        detectTapGestures {
            val now = SystemClock.elapsedRealtime()
            val timeSincePreviousClick = now - lastTapTimestampMs
            lastTapTimestampMs = now
            if (timeSincePreviousClick < 500) {
                ++consecutiveTaps
                if (consecutiveTaps == count) {
                    consecutiveTaps = 0
                    onTriggered()
                }
            } else {
                consecutiveTaps = 0
            }
        }
    }
