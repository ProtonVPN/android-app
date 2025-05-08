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

package com.protonvpn.android.base.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.proton.core.compose.theme.ProtonDimens
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultSmallUnspecified
import me.proton.core.compose.theme.defaultUnspecified

@Composable
@SuppressWarnings("LongParameterList")
fun ProtonSolidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    contained: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    colors: ButtonColors = ButtonDefaults.protonButtonColors(loading),
    contentPadding: PaddingValues = ButtonDefaults.ProtonContentPadding,
    content: @Composable () -> Unit,
) {
    ProtonButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonDefaults.MinHeight),
        enabled = enabled,
        loading = loading,
        contained = contained,
        interactionSource = interactionSource,
        elevation = ButtonDefaults.protonElevation(),
        shape = ProtonTheme.shapes.medium,
        border = null,
        style = ProtonTheme.typography.defaultUnspecified,
        colors = colors,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun ButtonDefaults.protonElevation() = ButtonDefaults.buttonElevation()

val ButtonDefaults.ProtonMinHeight get() = 48.dp

@Composable
fun ButtonDefaults.protonButtonColors(
    loading: Boolean = false,
    backgroundColor: Color = ProtonTheme.colors.interactionNorm,
    contentColor: Color = Color.White,
    disabledBackgroundColor: Color = if (loading) {
        ProtonTheme.colors.interactionPressed
    } else {
        ProtonTheme.colors.interactionDisabled
    },
    disabledContentColor: Color = if (loading) {
        Color.White
    } else {
        Color.White.copy(alpha = 0.5f)
    },
): ButtonColors = buttonColors(
    containerColor = backgroundColor,
    contentColor = contentColor,
    disabledContainerColor = disabledBackgroundColor,
    disabledContentColor = disabledContentColor,
)

@Composable
@SuppressWarnings("LongParameterList")
fun ProtonOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    contained: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    colors: ButtonColors = ButtonDefaults.protonOutlinedButtonColors(loading),
    border: BorderStroke = ButtonDefaults.protonOutlinedBorder(enabled, loading),
    contentPadding: PaddingValues = ButtonDefaults.ProtonContentPadding,
    content: @Composable () -> Unit,
) {
    ProtonButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        contained = contained,
        interactionSource = interactionSource,
        elevation = null,
        shape = ProtonTheme.shapes.medium,
        border = border,
        style = ProtonTheme.typography.defaultUnspecified,
        colors = colors,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun ButtonDefaults.protonOutlinedBorder(
    enabled: Boolean = true,
    loading: Boolean = false,
) = BorderStroke(
    1.0.dp,
    when {
        loading -> ProtonTheme.colors.interactionPressed
        !enabled -> ProtonTheme.colors.interactionDisabled
        else -> ProtonTheme.colors.textAccent
    },
)

@Composable
fun ButtonDefaults.protonOutlinedButtonColors(
    loading: Boolean = false,
    backgroundColor: Color = ProtonTheme.colors.backgroundNorm,
    contentColor: Color = ProtonTheme.colors.textAccent,
    disabledBackgroundColor: Color = if (loading) {
        ProtonTheme.colors.backgroundSecondary
    } else {
        ProtonTheme.colors.backgroundNorm
    },
    disabledContentColor: Color = if (loading) {
        ProtonTheme.colors.interactionPressed
    } else {
        ProtonTheme.colors.interactionDisabled
    },
): ButtonColors = buttonColors(
    containerColor = backgroundColor,
    contentColor = contentColor,
    disabledContainerColor = disabledBackgroundColor,
    disabledContentColor = disabledContentColor,
)

@Composable
@SuppressWarnings("LongParameterList")
fun ProtonTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    contained: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    colors: ButtonColors = ButtonDefaults.protonTextButtonColors(loading),
    style: TextStyle = ProtonTheme.typography.defaultSmallUnspecified,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTextStyle provides ProtonTheme.typography.body2Regular) {
        ProtonButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            loading = loading,
            contained = contained,
            interactionSource = interactionSource,
            elevation = null,
            shape = ProtonTheme.shapes.small,
            border = null,
            style = style,
            colors = colors,
            contentPadding = contentPadding,
            content = content,
        )
    }
}

@Composable
fun ButtonDefaults.protonTextButtonColors(
    loading: Boolean = false,
    backgroundColor: Color = if (loading) {
        ProtonTheme.colors.backgroundSecondary
    } else {
        Color.Transparent
    },
    contentColor: Color = ProtonTheme.colors.textAccent,
    disabledBackgroundColor: Color = if (loading) {
        ProtonTheme.colors.backgroundSecondary
    } else {
        Color.Transparent
    },
    disabledContentColor: Color = if (loading) {
        ProtonTheme.colors.interactionPressed
    } else {
        ProtonTheme.colors.textDisabled
    },
): ButtonColors = buttonColors(
    containerColor = backgroundColor,
    contentColor = contentColor,
    disabledContainerColor = disabledBackgroundColor,
    disabledContentColor = disabledContentColor,
)

private val ButtonDefaults.ProtonContentPadding: PaddingValues
    get() = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

@Composable
@SuppressWarnings("LongParameterList")
fun ProtonSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    colors: ButtonColors = ButtonDefaults.protonSecondaryButtonColors(loading),
    contentPadding: PaddingValues = ButtonDefaults.ProtonContentPadding,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTextStyle provides ProtonTheme.typography.captionRegular) {
        ProtonButton(
            onClick = onClick,
            modifier = modifier,
            minHeight = 32.dp,
            enabled = enabled,
            loading = loading,
            interactionSource = interactionSource,
            elevation = null,
            shape = ProtonTheme.shapes.medium,
            border = null,
            style = ProtonTheme.typography.defaultSmallUnspecified,
            colors = colors,
            contentPadding = contentPadding,
            content = content,
        )
    }
}

@Composable
fun ButtonDefaults.protonSecondaryButtonColors(
    loading: Boolean = false,
    backgroundColor: Color = ProtonTheme.colors.interactionWeakNorm,
    contentColor: Color = ProtonTheme.colors.textNorm,
    disabledBackgroundColor: Color = if (loading) {
        ProtonTheme.colors.interactionWeakPressed
    } else {
        ProtonTheme.colors.interactionWeakDisabled
    },
    disabledContentColor: Color = if (loading) {
        ProtonTheme.colors.textNorm
    } else {
        ProtonTheme.colors.textDisabled
    },
): ButtonColors = buttonColors(
    containerColor = backgroundColor,
    contentColor = contentColor,
    disabledContentColor = disabledContentColor,
    disabledContainerColor = disabledBackgroundColor,
)

@Suppress("LongParameterList")
@Composable
fun ProtonButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    contained: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    elevation: ButtonElevation?,
    shape: Shape,
    border: BorderStroke?,
    style: TextStyle,
    colors: ButtonColors,
    minHeight: Dp = ButtonDefaults.ProtonMinHeight,
    contentPadding: PaddingValues = ButtonDefaults.ProtonContentPadding,
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        enabled = !loading && enabled,
        interactionSource = interactionSource,
        elevation = elevation,
        shape = shape,
        border = border,
        colors = colors,
        contentPadding = contentPadding,
    ) {
        ProvideTextStyle(style) {
            ProtonButtonContent(
                loading = loading,
                contained = contained,
                content = content,
                progressColor = LocalContentColor.current,
            )
        }
    }
}

@Composable
private fun ProtonButtonContent(
    loading: Boolean = false,
    contained: Boolean = true,
    progressColor: Color,
    content: @Composable () -> Unit,
) {
    if (!contained) {
        Box(Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                content()
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(LoadingIndicatorBoxSize)
                        .padding((LoadingIndicatorBoxSize - LoadingIndicatorSize) / 2)
                        .align(Alignment.CenterEnd),
                    color = progressColor,
                    strokeWidth = LoadingIndicatorStroke,
                )
            }
        }
    } else {
        content()
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(start = ProtonDimens.DefaultSpacing)
                    .size(LoadingIndicatorBoxSize)
                    .padding((LoadingIndicatorBoxSize - LoadingIndicatorSize) / 2),
                color = progressColor,
                strokeWidth = LoadingIndicatorStroke,
            )
        }
    }
}

private val LoadingIndicatorBoxSize = 24.dp
private val LoadingIndicatorSize = 14.dp
private val LoadingIndicatorStroke = 1.dp

@Preview(
    widthDp = 640,
    showBackground = true,
    backgroundColor = android.graphics.Color.WHITE.toLong()
)
@Suppress("unused")
@Composable
private fun PreviewProtonSolidButton() {
    PreviewHelper { enabled, contained, loading ->
        ProtonSolidButton(
            enabled = enabled,
            contained = contained,
            loading = loading,
            onClick = { }
        ) {
            Text(text = "Button")
        }
    }
}

@Preview(widthDp = 640)
@Suppress("unused")
@Composable
private fun PreviewProtonOutlinedButton() {
    PreviewHelper { enabled, contained, loading ->
        ProtonOutlinedButton(
            enabled = enabled,
            contained = contained,
            loading = loading,
            onClick = { }
        ) {
            Text(text = "Button")
        }
    }
}

@Preview(widthDp = 640)
@Suppress("unused")
@Composable
private fun PreviewProtonTextButton() {
    PreviewHelper { enabled, contained, loading ->
        ProtonTextButton(
            enabled = enabled,
            contained = contained,
            loading = loading,
            onClick = { }
        ) {
            Text(text = "Button")
        }
    }
}

@Preview(widthDp = 640)
@Suppress("unused")
@Composable
private fun PreviewProtonSecondaryButton() {
    PreviewHelper { enabled, _, loading ->
        ProtonSecondaryButton(
            enabled = enabled,
            loading = loading,
            onClick = { }
        ) {
            Text(text = "Button")
        }
    }
}

@Composable
private inline fun PreviewHelper(
    crossinline button: @Composable (enabled: Boolean, contained: Boolean, loading: Boolean) -> Unit,
) {
    ProtonVpnPreview {
        Column(Modifier.padding(10.dp)) {
            PreviewRowHelper(
                enabled = true,
                loading = false,
                button,
            )
            PreviewRowHelper(
                enabled = false,
                loading = false,
                button,
            )
            PreviewRowHelper(
                enabled = true,
                loading = true,
                button,
            )
        }
    }
}

@Composable
private inline fun PreviewRowHelper(
    enabled: Boolean,
    loading: Boolean,
    button: @Composable (enabled: Boolean, contained: Boolean, loading: Boolean) -> Unit,
) {
    Row(Modifier.padding(bottom = 20.dp)) {
        Box(Modifier.width(320.dp)) {
            button(enabled, false, loading)
        }
        Box(
            Modifier
                .width(320.dp)
                .padding(start = 20.dp)) {
            button(enabled, true, loading)
        }
    }
}
