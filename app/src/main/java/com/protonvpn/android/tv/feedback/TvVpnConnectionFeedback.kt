/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.android.tv.feedback

import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.redesign.recents.ui.VpnConnectionFeedbackStaggeredEntrance
import com.protonvpn.android.redesign.recents.usecases.ConnectionFeedback
import com.protonvpn.android.tv.buttons.TvThumbFeedbackButton
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun TvVpnConnectionFeedback(
    showFeedback: Boolean,
    onFeedbackProvided: (ConnectionFeedback) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current

    var hasProvidedFeedback by remember {
        mutableStateOf(value = false)
    }

    val animationDelayMillis by remember {
        derivedStateOf {
            if (hasProvidedFeedback) 1100 else 0
        }
    }

    LaunchedEffect(key1 = showFeedback) {
        if (showFeedback) {
            hasProvidedFeedback = false
        }
    }

    AnimatedVisibility(
        visible = showFeedback,
        enter = EnterTransition.None,
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = 400,
                delayMillis = animationDelayMillis,
                easing = EaseInOut,
            ),
        ),
    ) {
        TvVpnConnectionFeedbackRequest(
            modifier = modifier,
            transition = transition,
            isFocusable = !hasProvidedFeedback,
            onConnectionFeedbackClick = { connectionFeedback ->
                hasProvidedFeedback = true

                view.focusSearch(View.FOCUS_DOWN)?.requestFocus()

                onFeedbackProvided(connectionFeedback)
            },
        )
    }
}

@Composable
private fun TvVpnConnectionFeedbackRequest(
    isFocusable: Boolean,
    transition: Transition<EnterExitState>,
    onConnectionFeedbackClick: (ConnectionFeedback) -> Unit,
    modifier: Modifier = Modifier,
    enterAnimationDelayMillis: Int = 700,
) {
    val focusRequester = remember { FocusRequester() }

    var isNegativeFeedbackPlaying by remember { mutableStateOf(value = false) }

    var isPositiveFeedbackPlaying by remember { mutableStateOf(value = false) }

    val isFeedbackButtonEnabled by remember {
        derivedStateOf { !isNegativeFeedbackPlaying && !isPositiveFeedbackPlaying }
    }

    Row(
        modifier = modifier
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    focusRequester.requestFocus()
                }
            }
            .focusProperties { canFocus = isFocusable }
            .focusable(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VpnConnectionFeedbackStaggeredEntrance(
            transition = transition,
            enterDelayMillis = enterAnimationDelayMillis,
            isInversed = true,
        ) {
            Text(
                text = stringResource(id = R.string.connection_card_label_connection_feedback),
                style = ProtonTheme.typography.body2Regular,
            )
        }

        Spacer(modifier = Modifier.width(width = 8.dp))

        VpnConnectionFeedbackStaggeredEntrance(
            transition = transition,
            enterDelayMillis = enterAnimationDelayMillis + 100,
            verticalOffsetDivider = 4,
            isInversed = true,
        ) {
            TvThumbFeedbackButton(
                modifier = Modifier.focusRequester(focusRequester = focusRequester),
                isPositive = false,
                isEnabled = isFeedbackButtonEnabled,
                isPlaying = isNegativeFeedbackPlaying,
                onClick = {
                    isNegativeFeedbackPlaying = true

                    onConnectionFeedbackClick(ConnectionFeedback.Negative)
                },
            )
        }

        VpnConnectionFeedbackStaggeredEntrance(
            transition = transition,
            enterDelayMillis = enterAnimationDelayMillis + 150,
            verticalOffsetDivider = 4,
            isInversed = true,
        ) {
            TvThumbFeedbackButton(
                isPositive = true,
                isEnabled = isFeedbackButtonEnabled,
                isPlaying = isPositiveFeedbackPlaying,
                onClick = {
                    isPositiveFeedbackPlaying = true

                    onConnectionFeedbackClick(ConnectionFeedback.Positive)
                },
            )
        }
    }
}
