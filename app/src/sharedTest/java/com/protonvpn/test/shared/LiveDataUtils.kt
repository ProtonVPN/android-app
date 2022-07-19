/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.test.shared

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.distinctUntilChanged

fun <T> runWhileCollectingLiveData(liveData: LiveData<T>, distinct: Boolean = false, block: () -> Unit): List<T> {
    val collectedStates = mutableListOf<T>()
    val observer = Observer<T> { collectedStates.add(it) }
    val observedLiveData = if (distinct) liveData.distinctUntilChanged() else liveData
    observedLiveData.observeForever(observer)
    try {
        block()
        return collectedStates
    } finally {
        observedLiveData.removeObserver(observer)
    }
}

