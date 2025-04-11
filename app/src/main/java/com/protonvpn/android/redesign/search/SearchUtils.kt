/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.redesign.search

import com.protonvpn.android.third_party.ApacheStringUtils

private val SERVER_SEARCH_ENHANCE_PATTERN = Regex("^[a-zA-Z-]+[0-9]+$")
private val ADDITIONAL_SEPARATORS = charArrayOf('-', '#')

fun addServerNameHash(term: String): String {
    return if (term.matches(SERVER_SEARCH_ENHANCE_PATTERN)) {
        val digitsStart = term.indexOfFirst { it in "0123456789" }
        term.substring(0, digitsStart) + "#" + term.substring(digitsStart)
    } else {
        term
    }
}

data class TextMatch(val index: Int, val length: Int, val fullText: String)

fun match(
    term: String,
    normalizedTerm: String,
    text: String,
    ignoreCase: Boolean = true,
    matchOnlyWordPrefixes: Boolean = true,
    additionalSeparators: CharArray = ADDITIONAL_SEPARATORS // Separators that are not whitespace
): com.protonvpn.android.redesign.search.TextMatch? {
    fun Char.isSeparator() = isWhitespace() || this in additionalSeparators

    val normalizedText = ApacheStringUtils.stripAccents(text)
    val idx = normalizedText.indexOf(normalizedTerm, ignoreCase = ignoreCase)
    return when {
        idx < 0 -> null
        !matchOnlyWordPrefixes
            || idx == 0
            || normalizedText[idx - 1].isSeparator()
            || normalizedText[idx].isSeparator() -> com.protonvpn.android.redesign.search.TextMatch(
            idx,
            term.length,
            text
        )
        else -> {
            var idxAcc = 0
            val matched = normalizedText.splitToSequence(*additionalSeparators).any { word ->
                word.startsWith(normalizedTerm, ignoreCase = ignoreCase).also { matched ->
                    if (!matched)
                        idxAcc += word.length + 1
                }
            }
            if (matched)
                com.protonvpn.android.redesign.search.TextMatch(idxAcc, term.length, text)
            else
                null
        }
    }
}
