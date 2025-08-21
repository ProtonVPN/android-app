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

package com.protonvpn.android.appconfig.periodicupdates

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * State values that can be used to expose state of update operations that use the PeriodicUpdateManager.
 * Use withUpdateState below for an easy to use wrapper.
 * Note: PeriodicUpdateManager ensures that no two identical operations run simultaneously.
 */
sealed interface UpdateState<in T : Any> {
    data class Idle<T : Any>(val lastResult: T?) : UpdateState<T>
    object Updating : UpdateState<Any>
}

suspend fun <T : Any, R> withUpdateState(
    updateState: MutableStateFlow<UpdateState<T>>,
    resultMapper: (R) -> T?,
    exceptionError: T,
    block: suspend () -> R,
): R {
    updateState.value = UpdateState.Updating
    return try {
        block().also {
            updateState.value = UpdateState.Idle(resultMapper(it))
        }
    } catch (e: Throwable) {
        updateState.value = when(e) {
            is CancellationException -> UpdateState.Idle(null)
            !is RuntimeException -> UpdateState.Idle(exceptionError)
            else -> throw e
        }
        throw e
    }
}
