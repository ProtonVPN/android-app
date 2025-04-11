/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.app.redesign.search

import com.protonvpn.android.redesign.search.TextMatch
import com.protonvpn.android.redesign.search.match
import org.junit.Test
import kotlin.test.assertEquals

class SearchUtilTests {

    @Test
    fun testmatch() {
        assertEquals(null, match("a", "a", "b"))
        assertEquals(null, match("a", "a", ""))
        assertEquals(TextMatch(0, 0, ""), match("", "", ""))
        assertEquals(TextMatch(0, 1, "a"), match("a", "a", "a"))
    }

    @Test
    fun testSearchMatchIgnoreCase() {
        assertEquals(null, match("kra", "kra", "Krakow", ignoreCase = false))
        assertEquals(TextMatch(0, 3, "Kraków"), match("kra", "kra", "Kraków", ignoreCase = true))
    }

    @Test
    fun testSearchMatchOnlyWords() {
        assertEquals(TextMatch(1, 2, "abc"), match("bc", "bc", "abc", matchOnlyWordPrefixes = false))
        assertEquals(null, match("bc", "bc", "abc", matchOnlyWordPrefixes = true))
        assertEquals(
            // Should match only second occurrence of "ab" (_ not defined as additional separator)
            TextMatch(5, 2, "_abc-abc"),
            match("ab", "ab", "_abc-abc", matchOnlyWordPrefixes = true, additionalSeparators = charArrayOf('-'))
        )
        // Allow matches for terms starting with separator
        assertEquals(TextMatch(2, 3, "PL#30"), match("#30", "#30", "PL#30", matchOnlyWordPrefixes = true))
    }

    @Test
    fun testSearchMatchAccents() {
        assertEquals(TextMatch(0, 5, "kraków"), match("krakó", "krako", "kraków"))
        assertEquals(TextMatch(7, 3, "miasto kraków"), match("kra", "kra", "miasto kraków"))
        // Normalization in some languages can affect length of the string (fewer UTF-16 chars), at least in theory.
        // Highlight should cover the original search term.
        assertEquals(TextMatch(0, 6, "kraków"), match(term = "kraków", normalizedTerm = "krako", text = "kraków"))
    }
}
