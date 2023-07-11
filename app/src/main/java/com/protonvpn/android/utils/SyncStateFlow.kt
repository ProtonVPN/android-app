/*
 * Copyright (c) 2021 Proton Technologies AG
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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import me.proton.core.util.kotlin.DefaultDispatcherProvider
import me.proton.core.util.kotlin.DispatcherProvider

// A StateFlow that blocks synchronous read until the first value is ready (i.e. emitted by baseFlow)
// USE WITH CAUTION.
class SyncStateFlow<T>(
    private val scope: CoroutineScope,
    private val baseFlow: Flow<T>,
    dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : StateFlow<T> {
    private val _state: Deferred<StateFlow<T>> = scope.async(dispatchers.Io) {
        baseFlow.stateIn(scope, SharingStarted.Eagerly, baseFlow.first())
    }

    private val flow: Flow<T> = flow {
        val readyFlow = _state.await()
        emitAll(readyFlow)
    }

    override val replayCache: List<T> get() = listOf(value)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val value: T get() = if (_state.isCompleted) {
        _state.getCompleted().value
    } else runBlocking {
        android.util.Log.w("SyncStateFlow", "blocking read")
        _state.await().value
    }

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        flow.collect(collector)
        throw RuntimeException()
    }
}
