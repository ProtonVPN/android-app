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

package com.protonvpn.android.redesign.base.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.flagResource
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.ProtonTheme3

private object FlagShapes {
    val regular = RoundedCornerShape(size = 4.dp)
    val small = RoundedCornerShape(size = 2.5.dp)
}

@Composable
fun Flag(
    exitCountry: CountryId,
    entryCountry: CountryId? = null,
    isSecureCore: Boolean = entryCountry != null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Flag(
        exitCountry.flagResource(context),
        entryCountry?.flagResource(context),
        isSecureCore,
        isFastest = exitCountry.isFastest,
        modifier = modifier
    )
}

@Composable
private fun Flag(
    exitCountryFlag: Int,
    entryCountryFlag: Int?,
    isSecureCore: Boolean,
    isFastest: Boolean,
    modifier: Modifier = Modifier
) {
    if (isSecureCore && entryCountryFlag != null) {
        Box(modifier = modifier.size(30.dp, 30.dp)) {
            Image(
                painterResource(id = entryCountryFlag),
                contentDescription = null,
                alignment = Alignment.Center,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(top = 15.dp)
                    .size(18.dp, 12.dp)
                    .clip(FlagShapes.small)
                    .drawWithCache {
                        val shadowPath = createScFlagShadow(Offset(4.dp.toPx(), -6.dp.toPx(),), 6.dp.toPx())
                        onDrawWithContent {
                            drawContent()
                            drawScFlagShadow(shadowPath, Color(0x66000000))
                        }
                    }
            )
            Image(
                painterResource(id = exitCountryFlag),
                contentDescription = null,
                alignment = Alignment.Center,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(top = 3.dp, start = 6.dp)
                    .size(24.dp, 16.dp)
                    .clip(FlagShapes.regular)
            )
        }
    } else {
        val drawScIndicatorModifier = if (isSecureCore) {
            val color =
                if (isFastest) ProtonTheme.colors.vpnGreen.copy(alpha = 0.2f)
                else ProtonTheme.colors.textHint
            Modifier.drawWithCache {
                val path = createScUnderlineArc(Size(26.dp.toPx(), 16.dp.toPx()), 6.dp.toPx())
                onDrawBehind {
                    // The underline arc is outside the layout area for this component, same as in
                    // designs. This should make it easier to position the flag.
                    drawScUnderlineArc(Offset(-4.dp.toPx(), 8.dp.toPx()), path, color)
                }
            }
        } else {
            Modifier
        }
        Image(
            painterResource(id = exitCountryFlag),
            contentDescription = null,
            alignment = Alignment.Center,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(30.dp, 20.dp)
                .then(drawScIndicatorModifier)
                .clip(FlagShapes.regular)
        )
    }
}

private fun CacheDrawScope.createScFlagShadow(offsetTopLeft: Offset, radius: Float) =
    Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(offsetTopLeft, size),
                bottomLeft = CornerRadius(radius)
            )
        )
    }

private fun DrawScope.drawScFlagShadow(path: Path, color: Color) {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    withTransform({
        if (isRtl) scale(-1f, 1f)
    }) {
        drawPath(path, color)
    }
}

private fun createScUnderlineArc(size: Size, radius: Float) =
    Path().apply {
        relativeLineTo(0f, size.height - radius)
        addArc(
            Rect(Offset(0f, size.height - 2 * radius), Size(2 * radius, 2 * radius)),
            180f,
            -90f
        )
        relativeLineTo(size.width - radius, 0f)
    }

private fun DrawScope.drawScUnderlineArc(offset: Offset, path: Path, color: Color) {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    withTransform(
        transformBlock = {
            if (isRtl) scale(-1f, 1f)
            translate(offset.x, offset.y)
        }
    ) {
        drawPath(path, color, style = Stroke(width = 1.85.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Preview
@Composable
private fun FlagPreviewRtlLight() {
    ProtonTheme3 {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FlagsPreviewHelper()
        }
    }
}

@Preview
@Composable
private fun FlagPreviewDark() {
    ProtonTheme3(isDark = true) {
        FlagsPreviewHelper()
    }
}

@Composable
private fun FlagsPreviewHelper() {
    Surface(color = ProtonTheme.colors.backgroundNorm) {
        Box(Modifier.padding(8.dp)) {
            Column {
                val modifier = Modifier.padding(8.dp)
                Flag(
                    exitCountryFlag = R.drawable.flag_fastest,
                    entryCountryFlag = null,
                    isSecureCore = true,
                    isFastest = true,
                    modifier = modifier
                )
                Flag(
                    exitCountryFlag = R.drawable.flag_au,
                    entryCountryFlag = R.drawable.flag_ch,
                    isSecureCore = true,
                    isFastest = false,
                    modifier = modifier
                )
                Flag(
                    exitCountryFlag = R.drawable.flag_us,
                    entryCountryFlag = null,
                    isSecureCore = false,
                    isFastest = false,
                    modifier = modifier
                )
            }
        }
    }
}