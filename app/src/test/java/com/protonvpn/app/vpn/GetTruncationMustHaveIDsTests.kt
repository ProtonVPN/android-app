/*
 * Copyright (c) 2023 Proton AG
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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.protonvpn.app.vpn

import com.protonvpn.android.vpn.usecases.GetTruncationMustHaveIDsImpl
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GetTruncationMustHaveIDsTests {

    private lateinit var testScope: TestScope
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `all slots are filled when there are overlapping items`() = testScope.runTest {
        val result = GetTruncationMustHaveIDsImpl.getMustHaveIDs(
            currentServerID = "r2",
            recentsServerIDs = listOf("r1", "r2", "r3", "r4"),
            profilesServerIDs = listOf("r3", "p5", "p6", "p7"),
            transientIDs = listOf("p5"),
            maxRecents = 2,
            maxMustHaves = 5
        )
        assertEquals(setOf("r2", "r1", "r3", "p5", "p6"), result)
    }

    @Test
    fun `remaining slots are filled with additional recents`() = testScope.runTest {
        val result = GetTruncationMustHaveIDsImpl.getMustHaveIDs(
            currentServerID = null,
            recentsServerIDs = listOf("r1", "r2", "r3", "r4", "r5"),
            profilesServerIDs = listOf("r1", "r2", "p1"),
            transientIDs = emptyList(),
            maxRecents = 2,
            maxMustHaves = 5
        )
        assertEquals(setOf("r1", "r2", "p1", "r3", "r4"), result)
    }

    @Test
    fun `transient IDs are added with second highest priority`() = testScope.runTest {
        val result = GetTruncationMustHaveIDsImpl.getMustHaveIDs(
            currentServerID = "current",
            recentsServerIDs = listOf("r1", "r2", "r3"),
            profilesServerIDs = listOf("p1", "p2", "p3"),
            transientIDs = listOf("t1", "t2"),
            maxRecents = 3,
            maxMustHaves = 5
        )
        assertEquals(setOf("current", "t1", "t2", "r1", "r2"), result)
    }

    @Test
    fun `no servers produce empty result`() = testScope.runTest {
        val result = GetTruncationMustHaveIDsImpl.getMustHaveIDs(
            currentServerID = null,
            recentsServerIDs = emptyList(),
            profilesServerIDs = emptyList(),
            transientIDs = emptyList(),
            maxRecents = 2,
            maxMustHaves = 5
        )
        assertEquals(emptySet<String>(), result)
    }
}
