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

import com.protonvpn.android.concurrency.VpnDispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher

class TestDispatcherProvider(
    testDispatcher: CoroutineDispatcher,
    private val singleThreadDispatcherFactory: () -> CoroutineDispatcher = { testDispatcher }
) : VpnDispatcherProvider {
    override val Io: CoroutineDispatcher = testDispatcher
    override val infiniteIo: CoroutineDispatcher = testDispatcher
    override val Comp: CoroutineDispatcher = testDispatcher
    override val Main: CoroutineDispatcher = testDispatcher
    override fun newSingleThreadDispatcher(): CoroutineDispatcher = singleThreadDispatcherFactory()
    override fun newSingleThreadDispatcherForInifiniteIo(): CoroutineDispatcher = newSingleThreadDispatcher()
}
