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

package com.protonvpn.android.concurrency

import kotlinx.coroutines.CoroutineDispatcher
import me.proton.core.util.kotlin.DispatcherProvider

interface VpnDispatcherProvider : DispatcherProvider {
    /**
     * A dispatcher for running tasks that block (almost) infinitely, like reading from logcat.
     * Use the Io dispatcher for regular IO tasks.
     */
    val infiniteIo: CoroutineDispatcher
}
