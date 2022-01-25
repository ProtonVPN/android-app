/*
 * Copyright (c) 2019 Proton Technologies AG
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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

// Transformations.map equivalent that always serves non-null and up-to-date value (even when no
// observers are attached)
@Deprecated("Use flows instead")
inline fun <S, reified T : Any> LiveData<S>.eagerMapNotNull(
    ignoreIfEqual: Boolean = false,
    crossinline transform: (S?) -> T
): MediatorLiveData<T> {
    val mediator = MediatorLiveData<T>()
    mediator.addSource(this) {
        val newValue = transform(value)
        if (!ignoreIfEqual || mediator.value != newValue) {
            mediator.value = newValue
        }
    }
    // Acquire non-null value even when source is null
    if (value == null) {
        mediator.value = transform(null)
    }
    // Force update of value even if there are no observers
    mediator.observeForever {}
    return mediator
}

fun <S1, S2, R> mapMany(
    source1: LiveData<S1>,
    source2: LiveData<S2>,
    transform: (S1, S2) -> R
): LiveData<R> = MediatorLiveData<R>().apply {
    fun update(s1: S1?, s2: S2?) {
        if (s1 != null && s2 != null)
            value = transform(s1, s2)
    }
    addSource(source1) { update(it, source2.value) }
    addSource(source2) { update(source1.value, it) }
}
