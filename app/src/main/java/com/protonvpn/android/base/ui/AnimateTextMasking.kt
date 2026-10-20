/*
 * Copyright (c) 2026. Proton AG
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

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Replaces the characters of text with maskCharacter one by one, in random order.
 * The width is fixed on the first measurement so that the layout doesn't change as the characters are replaced.
 * Span styles are kept, the masking doesn't change the length of the text.
 */
@Composable
fun AnimateTextMasking(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    stepDurationMs: Long = 50,
    maskCharacter: Char = '*',
    preserveCharacters: CharArray = charArrayOf('.', ' ', '-'),
) {
    val displayText = remember(text) { mutableStateOf(text.text) }
    val fixedWidth = remember(text) { mutableStateOf<Int?>(null) }

    LaunchedEffect(text) {
        // The width needs to be fixed before replacing characters changes it.
        snapshotFlow { fixedWidth.value }.filterNotNull().first()
        val indicesToReplace = text.indices
            .filter { !preserveCharacters.contains(text[it]) }
            .shuffled()

        indicesToReplace.forEach { replacementIndex ->
            delay(stepDurationMs)
            displayText.value = displayText.value.replaceRange(
                replacementIndex,
                replacementIndex + 1,
                maskCharacter.toString()
            )
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Layout(
            content = {
                BasicText(
                    AnnotatedString(displayText.value, text.spanStyles, text.paragraphStyles),
                    style = style,
                    modifier = Modifier.onGloballyPositioned {
                        if (fixedWidth.value == null) {
                            fixedWidth.value = it.size.width
                        }
                    },
                )
            },
            modifier = modifier,
            measurePolicy = { measurables, constraints ->
                val placeable = measurables.first().measure(constraints)
                val width = fixedWidth.value ?: placeable.width
                val offsetX = (width - placeable.width) / 2
                layout(width, placeable.height) {
                    placeable.placeRelative(offsetX, 0)
                }
            }
        )
    }
}
