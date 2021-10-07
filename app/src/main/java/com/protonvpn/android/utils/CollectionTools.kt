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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.proton.core.util.kotlin.mapNotNullAsync
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

// Checks predicate in parallel (via scope.launch {}) for each item and returns element that first finished with true or
// null.
@OptIn(ExperimentalStdlibApi::class)
suspend fun <T> List<T>.parallelFirstOrNull(predicate: suspend (T) -> Boolean): T? = coroutineScope {
    var count = size
    if (count == 0)
        null
    else {
        val responses = MutableSharedFlow<T?>(replay = count)
        val workersScope = CoroutineScope(coroutineContext[CoroutineDispatcher.Key] ?: Dispatchers.IO)
        try {
            forEach { item ->
                workersScope.launch {
                    responses.emit(item.takeIf { predicate(it) })
                }
            }
            responses.first {
                count--
                it != null || count == 0
            }
        } finally {
            workersScope.cancel()
        }
    }
}

// Search for elements satisfying [predicate] in parallel. [returnAll] = true will find all elements, otherwise only
// first (fastest) element is returned.
suspend fun <T : Any> List<T>.parallelSearch(returnAll: Boolean, predicate: suspend (T) -> Boolean): List<T> =
    if (returnAll)
        mapNotNullAsync { item -> item.takeIf { predicate(it) } }
    else
        listOfNotNull(parallelFirstOrNull { predicate(it) })
