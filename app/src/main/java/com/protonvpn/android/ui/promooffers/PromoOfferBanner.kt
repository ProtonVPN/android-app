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

package com.protonvpn.android.ui.promooffers

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.protonvpn.android.R
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.base.ui.upsellBorderGradientEnd
import com.protonvpn.android.base.ui.upsellBorderGradientStart
import com.protonvpn.android.utils.tickFlow
import kotlinx.coroutines.launch
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import me.proton.core.presentation.R as CoreR

@Stable
@VisibleForTesting
class CountDownState(
    private val endTimestamp: Long,
    @WallClock private val clock: () -> Long
) {
    private val timeLeftState = mutableLongStateOf(endTimestamp - clock())

    val timeLeftText: String
        @Composable
        get() = getTimeLeftText(timeLeftState.longValue)

    suspend fun countDown() = tickFlow(1.seconds, clock).collect { now ->
        timeLeftState.longValue = endTimestamp - now
    }

    @Composable
    private fun getTimeLeftText(rawTimeLeftMs: Long): String {
        val timeLeft = rawTimeLeftMs.coerceAtLeast(0).milliseconds
        return timeLeft.toComponents { d, hours, minutes, seconds, _ ->
            val days = d.toInt()
            val (largeUnits, smallUnits) = when {
                days > 0 -> Pair(
                    pluralStringResource(id = R.plurals.time_left_days, count = days, days),
                    pluralStringResource(R.plurals.time_left_hours, count = hours, hours)
                )
                hours > 0 -> Pair(
                    pluralStringResource(R.plurals.time_left_hours, count = hours, hours),
                    pluralStringResource(R.plurals.time_left_minutes, count = minutes, minutes)
                )
                else -> Pair(
                    pluralStringResource(R.plurals.time_left_minutes, count = minutes, minutes),
                    pluralStringResource(R.plurals.time_left_seconds, count = seconds, seconds)
                )
            }
            stringResource(R.string.offer_time_left, largeUnits, smallUnits)
        }
    }
}

@Composable
private fun rememberCountDownState(endTimestamp: Long) =
    remember(endTimestamp) { CountDownState(endTimestamp, System::currentTimeMillis) }

@Composable
fun PromoOfferBanner(
    state: PromoOfferBannerState,
    onClick: suspend () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
      modifier = modifier
    ) {
        BannerWithCountdown(
            onClick,
            state.imageUrl,
            state.alternativeText,
            state.endTimestamp,
            modifier = if (state.isDismissible) Modifier.padding(top = 14.dp) else Modifier
        )
        if (state.isDismissible) {
            DismissButton(
                onClick = onDismiss,
                contentDescription = stringResource(R.string.notification_banner_dismiss_accessibility_label),
                modifier = Modifier.layoutWithRelativeOffset((-8).dp)
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun BannerWithCountdown(
    onClick: suspend () -> Unit,
    imageUrl: String,
    alternativeText: String,
    countDownEndTimestamp: Long?,
    modifier: Modifier = Modifier,
) {
    var isProcessing by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(targetValue = if (isProcessing) 0.5f else 1f)
    val coroutineScope = rememberCoroutineScope()
    val clickAction: () -> Unit = remember(onClick) {
        {
            coroutineScope.launch {
                isProcessing = true
                onClick()
                isProcessing = false
            }
        }
    }
    val bannerShape = ProtonTheme.shapes.large
    Column(
        modifier = modifier
            .background(ProtonTheme.colors.backgroundSecondary, bannerShape)
            .border(1.dp, PromoBorderBrush, bannerShape)
            .clickable(onClick = clickAction, enabled = !isProcessing)
            .clip(bannerShape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val imageModifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = contentAlpha }
        GlideImage(
            model = imageUrl,
            contentDescription = alternativeText,
            contentScale = ContentScale.FillWidth,
            modifier = imageModifier,
        )
        if (countDownEndTimestamp != null) {
            val countDownState = rememberCountDownState(endTimestamp = countDownEndTimestamp)
            LaunchedEffect(countDownState) {
                countDownState.countDown()
            }
            Text(
                text = countDownState.timeLeftText,
                style = ProtonTheme.typography.captionWeak,
                modifier = Modifier.graphicsLayer { alpha = contentAlpha }
            )
        }
    }
}

@Composable
private fun DismissButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val colorSeparation = ProtonTheme.colors.backgroundNorm
    val colorOutline = ProtonTheme.colors.separatorNorm
    val colorFill = ProtonTheme.colors.backgroundSecondary
    Image(
        painterResource(id = CoreR.drawable.ic_proton_cross_small),
        colorFilter = ColorFilter.tint(ProtonTheme.colors.iconNorm),
        contentDescription = contentDescription,
        modifier = modifier
            .drawBehind {
                drawCircle(color = colorSeparation)
                drawCircle(color = colorOutline, radius = size.minDimension / 2 - 2.dp.toPx())
                drawCircle(color = colorFill, radius = size.minDimension / 2 - 3.dp.toPx())
            }
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp)
            .size(16.dp)
    )
}


private val PromoBorderBrush: Brush @Composable get() = Brush.linearGradient(
    colors = with(ProtonTheme.colors) { listOf(upsellBorderGradientStart, upsellBorderGradientEnd) }
)

// It's easier to apply custom layout than to juggle paddings.
private fun Modifier.layoutWithRelativeOffset(horizontal: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val horizontalOffset = horizontal.toPx().roundToInt()
    layout(placeable.width, placeable.height) {
        placeable.placeRelative(constraints.maxWidth - placeable.width - horizontalOffset, 0)
    }
}
