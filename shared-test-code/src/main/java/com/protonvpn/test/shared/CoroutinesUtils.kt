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

import app.cash.turbine.TurbineTestContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope

@Deprecated("Use turbine instead")
fun <T> TestScope.runWhileCollecting(flow: Flow<T>, block: suspend () -> Unit): List<T> {
    val collectedValues = mutableListOf<T>()
    val collectJob = backgroundScope.launch {
        flow.collect {
            collectedValues += it
        }
    }
    try {
        runBlocking {
            block()
        }
        return collectedValues
    } finally {
        collectJob.cancel()
    }
}

/** Calls awaitItem in a loop until a matching value is returned.
 *
 * Prefer awaitItem where possible. Use awaitItemThatMatches when testing state flows that can emit multiple values in
 * response to update.
 */
@Deprecated("Use runCurrent() + expectMostRecentItem()")
suspend fun <T> TurbineTestContext<T>.awaitMatchingItem(predicate: (T) -> Boolean): T {
    var item: T
    do {
        item = awaitItem()
    } while(!predicate(item))
    return item
}
