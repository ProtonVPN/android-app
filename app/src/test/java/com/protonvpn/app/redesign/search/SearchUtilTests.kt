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

import com.protonvpn.android.redesign.search.ui.TextMatch
import com.protonvpn.android.redesign.search.ui.match
import org.junit.Test
import kotlin.test.assertEquals

class SearchUtilTests {

    @Test
    fun testmatch() {
        assertEquals(null, match("a", "b"))
        assertEquals(null, match("a", ""))
        assertEquals(TextMatch(0, 0, ""), match("", ""))
        assertEquals(TextMatch(0, 1, "a"), match("a", "a"))
    }

    @Test
    fun testSearchMatchIgnoreCase() {
        assertEquals(null, match("kra", "Krakow", ignoreCase = false))
        assertEquals(TextMatch(0, 3, "Kraków"), match("kra", "Kraków", ignoreCase = true))
    }

    @Test
    fun testSearchMatchOnlyWords() {
        assertEquals(TextMatch(1, 2, "abc"), match("bc", "abc", matchOnlyWordPrefixes = false))
        assertEquals(null, match("bc", "abc", matchOnlyWordPrefixes = true))
        assertEquals(
            // Should match only second occurrence of "ab" (_ not defined as additional separator)
            TextMatch(5, 2, "_abc-abc"),
            match("ab", "_abc-abc", matchOnlyWordPrefixes = true, additionalSeparators = charArrayOf('-'))
        )
    }

    @Test
    fun testSearchMatchAccents() {
        assertEquals(null, match("krakow", "kraków", removeAccents = false))
        assertEquals(TextMatch(0, 3, "kraków"), match("kra", "kraków", removeAccents = true))
        assertEquals(TextMatch(7, 3, "miasto kraków"), match("kra", "miasto kraków", removeAccents = true))
    }
}