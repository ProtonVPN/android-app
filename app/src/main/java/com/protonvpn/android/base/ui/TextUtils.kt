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

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString

/**
 * Replaces a SINGLE occurrence of "replace" with inline content annotation.
 */
fun String.replaceWithInlineContent(replace: String, inlineContentId: String): AnnotatedString {
    val tokenIndex = indexOf(replace)
    return if (tokenIndex >= 0) {
        val beforeFlags = substring(0, tokenIndex)
        val afterFlags = substring(tokenIndex + replace.length)
        buildAnnotatedString {
            append(beforeFlags)
            appendInlineContent(inlineContentId)
            append(afterFlags)
        }
    } else {
        AnnotatedString(this)
    }
}
