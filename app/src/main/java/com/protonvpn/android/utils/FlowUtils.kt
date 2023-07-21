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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

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

fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> {
    val resultFlow = MutableStateFlow(transform(this.value))
    onEach { newValue -> resultFlow.value = transform(newValue) }
    return resultFlow
}
