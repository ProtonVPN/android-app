/*
 * Copyright (c) 2017 Proton Technologies AG
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
package com.protonvpn.android.utils

import java.text.Collator
import java.util.Locale

fun <T> Collection<T>.randomNullable() =
        if (isEmpty()) null else random()

// locale-aware sortedBy e.g. a < ą < b
inline fun <T> Iterable<T>.sortedByLocaleAware(crossinline selector: (T) -> String): List<T> {
    val c = Collator.getInstance(Locale.getDefault())
    return sortedWith(Comparator { s1, s2 ->
        c.compare(selector(s1), selector(s2))
    })
}
