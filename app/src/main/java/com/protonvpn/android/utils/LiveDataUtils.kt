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
