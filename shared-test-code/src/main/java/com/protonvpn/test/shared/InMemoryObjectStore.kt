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

package com.protonvpn.test.shared

import com.protonvpn.android.models.vpn.ServersStore
import com.protonvpn.android.utils.ObjectStore

class InMemoryObjectStore<T> : ObjectStore<T> {
    private var value: T? = null
    override suspend fun read(): T? = value
    override fun store(data: T) { value = data }
    override fun clear() { value = null }
}

fun createInMemoryServersStore() =
    ServersStore(InMemoryObjectStore())
