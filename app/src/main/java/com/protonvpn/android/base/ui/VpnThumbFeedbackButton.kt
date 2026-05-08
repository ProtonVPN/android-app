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

package com.protonvpn.android.base.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.protonvpn.android.R

@Composable
fun VpnThumbFeedbackButton(
    isPositive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isPlaying: Boolean = false,
) {
    val (animationResId, verticalOffset) = remember(key1 = isPositive) {
        if (isPositive) R.raw.feedback_thumbs_up_animation to 0.dp
        else R.raw.feedback_thumbs_down_animation to 2.dp
    }

    val contentDescription = stringResource(
        id = if (isPositive) {
            R.string.accessibility_feedback_positive
        } else {
            R.string.accessibility_feedback_negative
        },
    )

    val composition = rememberLottieComposition(
        spec = LottieCompositionSpec.RawRes(resId = animationResId),
    )

    val progress by animateLottieCompositionAsState(
        composition = composition.value,
        isPlaying = isPlaying,
    )

    Box(
        modifier = modifier
            .offset { IntOffset(x = 0, y = verticalOffset.toPx().toInt()) }
            .clip(shape = CircleShape)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                if (!isEnabled) {
                    disabled()
                }
            }
            .optional(
                predicate = { isEnabled },
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(id = R.string.accessibility_action_submit_feedback),
                    onClick = onClick,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        LottieAnimation(
            modifier = Modifier
                .offset { IntOffset(x = 0, y = -verticalOffset.toPx().toInt()) }
                .clearAndSetSemantics { },
            composition = composition.value,
            progress = { progress },
        )
    }
}
