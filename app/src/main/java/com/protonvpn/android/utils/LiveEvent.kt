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

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.util.concurrent.atomic.AtomicLong

/**
 * Lifecycle aware event emitter. It'll not notify when observer registers for an event (only after
 * actual emit() call). If multiple emits happen when observer is not active only a single
 * notification will be delivered.
 */
class LiveEvent {

    private val data = MutableLiveData<Long>()
    private val atomicCounter = AtomicLong()

    fun emit() {
        val newCounter = atomicCounter.incrementAndGet()
        if (ArchTaskExecutor.getInstance().isMainThread) {
            data.value = newCounter
        } else {
            data.postValue(newCounter)
        }
    }

    fun observe(lifecycleOwner: LifecycleOwner, notify: () -> Unit) {
        val onBindValue = data.value
        data.observe(lifecycleOwner, Observer {
            if (data.value != onBindValue) {
                notify()
            }
        })
    }

    fun observeForever(notify: () -> Unit) {
        val onBindValue = data.value
        data.observeForever {
            if (data.value != onBindValue) {
                notify()
            }
        }
    }
}
