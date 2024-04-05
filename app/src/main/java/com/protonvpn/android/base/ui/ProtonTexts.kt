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
package com.protonvpn.android.base.ui

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun AnnotatedClickableText(
    fullText: String,
    annotatedPart: String,
    onAnnotatedClick: () -> Unit,
    onAnnotatedOutsideClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    style: TextStyle = ProtonTheme.typography.body1Regular,
    color: Color = Color.Unspecified,
) {
    val annotatedString = buildAnnotatedString {
        val startIndex = fullText.indexOf(annotatedPart)
        val endIndex = startIndex + annotatedPart.length

        append(fullText)

        if (startIndex >= 0) {
            addStyle(
                style = SpanStyle(
                    color = ProtonTheme.colors.textAccent,
                    textDecoration = TextDecoration.None
                ),
                start = startIndex,
                end = endIndex
            )
            addStringAnnotation(
                tag = "URL",
                annotation = "annotation",
                start = startIndex,
                end = endIndex
            )
        }
    }

    val textColor = color.takeOrElse {
        style.color.takeOrElse {
            LocalContentColor.current
        }
    }
    val textStyle = style.merge(color = textColor)
    ClickableText(
        text = annotatedString,
        style = textStyle,
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                onAnnotatedClick()
            } ?: onAnnotatedOutsideClick?.invoke()
        }
    )
}
