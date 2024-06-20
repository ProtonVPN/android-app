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

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.tv.ui.ProtonTvDialogBasic
import com.protonvpn.android.tv.ui.onFocusLost
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun TvSettingsReconnectDialog(
    onReconnectNow: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ProtonTvDialogBasic(
        onDismissRequest = onDismissRequest
    ) { focusRequester ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.settings_dialog_reconnect),
                style = ProtonTheme.typography.headline
            )

            // Remove when implemented in Compose TV: https://issuetracker.google.com/issues/268268856
            // (can't use the suggestion from issuetracker with `clickable` modifier because it breaks the `Surface`
            // selection).
            val context = LocalContext.current
            val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
            val focusSoundModifier = Modifier.onFocusLost {
                audioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP)
            }

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onDismissRequest,
                    modifier = focusSoundModifier
                ) {
                    Text(stringResource(R.string.ok))
                }
                Spacer(Modifier.width(16.dp))
                Button(
                    onClick = onReconnectNow,
                    modifier = focusSoundModifier.focusRequester(focusRequester)
                ) {
                    Text(stringResource(R.string.reconnect_now))
                }
            }
        }
    }
}
