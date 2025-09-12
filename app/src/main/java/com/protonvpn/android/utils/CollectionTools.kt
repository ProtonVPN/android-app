/*
 * Copyright (c) 2017 Proton AG
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.proton.core.util.kotlin.mapNotNullAsync
import java.text.Collator
import java.util.Locale
import kotlin.random.Random

fun <T> Collection<T>.randomNullable() =
        if (isEmpty()) null else random()

// locale-aware sortedBy e.g. a < ą < b
inline fun <T> Iterable<T>.sortedByLocaleAware(locale: Locale? = null, crossinline selector: (T) -> String): List<T> {
    val c = Collator.getInstance(locale ?: Locale.getDefault())
    return sortedWith(Comparator { s1, s2 ->
        c.compare(selector(s1), selector(s2))
    })
}

// Checks predicate in parallel (via scope.launch {}) for each item and returns element that first finished with true or
// null.
// If [priorityWaitMs] > 0 then it'll prioritize elements in terms of order and:
// - before priorityWaitMs passed it'll return successful element only if all previous elements failed (finished and
// didn't meet the predicate)
// - after priorityWaitMs returns first in terms of order element that did succeed
// - if no element was returned so far it waits for first to meet the predicate or return null if all failed
@OptIn(ExperimentalStdlibApi::class)
suspend fun <T : Any> List<T>.parallelFirstOrNull(
    priorityWaitMs: Long = 0L,
    predicate: suspend (T) -> Boolean
): T? = coroutineScope {
    data class WorkerResult(val success: Boolean, val index: Int)
    fun Array<Boolean?>.havePriorityResult(): Boolean {
        forEach {
            if (it == null)
                return false
            else if (it == true)
                return true
        }
        return true
    }
    if (size == 0)
        null
    else {
        val resultChannel = Channel<WorkerResult>()
        val workersScope = CoroutineScope(coroutineContext[CoroutineDispatcher.Key] ?: Dispatchers.IO)
        try {
            val workerJobs = mapIndexed { i, item ->
                workersScope.launch {
                    resultChannel.send(WorkerResult(predicate(item), i))
                }
            }
            workersScope.launch {
                workerJobs.joinAll()
                resultChannel.close()
            }

            val firstSuccessIdx: Int = if (priorityWaitMs > 0) {
                val responses = Array<Boolean?>(size) { null }
                withTimeoutOrNull(priorityWaitMs) {
                    do {
                        resultChannel.receiveCatching().getOrNull()?.also { result ->
                            responses[result.index] = result.success
                        }
                    } while (!responses.havePriorityResult())
                }
                responses.indexOfFirst { it == true }
            } else -1

            val resultIdx: Int? = if (firstSuccessIdx >= 0)
                firstSuccessIdx
            else
                resultChannel.receiveAsFlow().firstOrNull { it.success }?.index
            resultIdx?.let { get(it) }
        } finally {
            workersScope.cancel()
        }
    }
}

// Search for elements satisfying [predicate] in parallel. [returnAll] = true will find all elements, otherwise only
// first (fastest) element is returned.
suspend fun <T : Any> List<T>.parallelSearch(
    returnAll: Boolean,
    priorityWaitMs: Long = 0L,
    predicate: suspend (T) -> Boolean
): List<T> =
    if (returnAll)
        mapNotNullAsync { item -> item.takeIf { predicate(it) } }
    else
        listOfNotNull(parallelFirstOrNull(priorityWaitMs = priorityWaitMs) { predicate(it) })

fun <T> List<T>.swapOrCurrent(index1: Int, index2: Int): List<T> {
    if (index1 == index2) return this

    if (index1 !in indices) return this

    if (index2 !in indices) return this

    return this.toMutableList()
        .apply {
            val temp = this[index1]
            this[index1] = this[index2]
            this[index2] = temp
        }
        .toList()
}

// as .take(n) but instead of taking n first elements take random elements keeping the order
fun <T> List<T>.takeRandomStable(n: Int, random: Random = Random.Default): List<T> =
    if (n >= size)
        this
    else ArrayList(this).apply {
        repeat(size - n) {
            removeAt(random.nextInt(size))
        }
    }

fun <K,V> MutableMap<K, MutableList<V>>.addToList(k: K, v: V) {
    getOrPut(k) { mutableListOf() }.add(v)
}

fun <K,V> MutableMap<K, MutableSet<V>>.addToSet(k: K, v: V) {
    getOrPut(k) { mutableSetOf() }.add(v)
}

fun <T> Iterable<T>.replace(newItem: T, predicate: (T) -> Boolean) = map {
    if (predicate(it)) newItem else it
}
