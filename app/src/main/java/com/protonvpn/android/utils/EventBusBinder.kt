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

import com.protonvpn.android.bus.EventBus

// Helper class for registering/unregistering given object with EventBus.
// Keeps track of current state so that e.g. two subsequent calls
// to unregister won't lead to crash.
class EventBusBinder(private val subject: Any) {

    private var registered = false

    fun register() {
        if (!registered) {
            EventBus.getInstance().register(subject)
            registered = true
        }
    }

    fun unregister() {
        if (registered) {
            EventBus.getInstance().unregister(subject)
            registered = false
        }
    }
}
