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

package com.protonvpn.android.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun <T> Flow<T>.withPrevious(): Flow<Pair<T, T>> = flow {
    var isFirstElement = true
    var previous: T? = null
    collect { value ->
        if (isFirstElement) {
            isFirstElement = false
        } else {
            @Suppress("UNCHECKED_CAST")
            emit(Pair(previous as T, value))
        }
        previous = value
    }
}

// Returns transformed flow if there is a value, otherwise empty flow.
@OptIn(ExperimentalCoroutinesApi::class)
fun <T, R> Flow<T?>.flatMapLatestNotNull(transform: suspend (T) -> Flow<R>): Flow<R> =
    flatMapLatest { value ->
        if (value == null) emptyFlow()
        else transform(value)
    }

/**
 * A convenience operator for simple cases of mapping one StateFlow to another.
 * Keep in mind that the transform is applied each time value is read so it should be lightweight.
 */
fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> {
    val source = this
    return object : StateFlow<R> {
        override val replayCache: List<R>
            get() = listOf(value)
        override val value: R
            get() = transform(source.value)

        override suspend fun collect(collector: FlowCollector<R>): Nothing {
            source.collect { value -> collector.emit(transform(value)) }
        }
    }
}

fun tickFlow(step: Duration, clock: () -> Long) = flow {
    var lastTick = clock()
    var delay = step
    while (true) {
        emit(lastTick)
        delay(delay)
        val now = clock()
        val excessDelay = (now - lastTick).milliseconds - delay
        delay = (step - excessDelay).coerceAtLeast(Duration.ZERO)
        lastTick = now
    }
}

// Util serving as a safer alternative for suspendCancellableCoroutine (where resume throws exception
// when called after cancellation and need to be guarded with isActive).
suspend fun <T> suspendForCallback(
    onClose: () -> Unit,
    registerCallback: (resume: (T) -> ChannelResult<Unit>) -> Unit
): T? =
    callbackFlow {
        registerCallback { trySend(it) }
        awaitClose { onClose() }
    }.first()

suspend fun <T> suspendForCallbackWithTimeout(
    timeoutMs: Long,
    onClose: () -> Unit,
    registerCallback: (resume: (T) -> ChannelResult<Unit>) -> Unit
): T? =
    withTimeoutOrNull(timeoutMs) {
        suspendForCallback(onClose, registerCallback)
    }

inline fun <T1, T2, T3, T4, T5, T6, R> combine(
    f1: Flow<T1>,
    f2: Flow<T2>,
    f3: Flow<T3>,
    f4: Flow<T4>,
    f5: Flow<T5>,
    f6: Flow<T6>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6) -> R,
): Flow<R> = kotlinx.coroutines.flow.combine(f1, f2, f3, f4, f5, f6) { args: Array<*> ->
    @Suppress("UNCHECKED_CAST")
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
    )
}

inline fun <T1, T2, T3, T4, T5, T6, T7, R> combine(
    f1: Flow<T1>,
    f2: Flow<T2>,
    f3: Flow<T3>,
    f4: Flow<T4>,
    f5: Flow<T5>,
    f6: Flow<T6>,
    f7: Flow<T7>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7) -> R,
): Flow<R> = kotlinx.coroutines.flow.combine(f1, f2, f3, f4, f5, f6, f7) { args: Array<*> ->
    @Suppress("UNCHECKED_CAST")
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7
    )
}

inline fun <T1, T2, T3, T4, T5, T6, T7, T8, R> combine(
    f1: Flow<T1>,
    f2: Flow<T2>,
    f3: Flow<T3>,
    f4: Flow<T4>,
    f5: Flow<T5>,
    f6: Flow<T6>,
    f7: Flow<T7>,
    f8: Flow<T8>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8) -> R,
): Flow<R> = kotlinx.coroutines.flow.combine(f1, f2, f3, f4, f5, f6, f7, f8) { args: Array<*> ->
    @Suppress("UNCHECKED_CAST")
    transform(
        args[0] as T1,
        args[1] as T2,
        args[2] as T3,
        args[3] as T4,
        args[4] as T5,
        args[5] as T6,
        args[6] as T7,
        args[7] as T8,
    )
}
