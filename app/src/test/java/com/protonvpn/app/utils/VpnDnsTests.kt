/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.app.utils

import com.protonvpn.android.vpn.SYSTEM_DNS_HEAD_START
import com.protonvpn.android.vpn.VpnDns
import com.protonvpn.test.shared.TestDispatcherProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnDnsTests {

    lateinit var testScope: TestScope
    lateinit var testDispatcherProvider: TestDispatcherProvider

    var calledSystem = false
    var calledProton = false

    suspend fun longSystemResolver(hostname: String): List<InetAddress> {
        calledSystem = true
        delay(100)
        return listOf(InetAddress.getByAddress(byteArrayOf(1,1,1,1)))
    }
    suspend fun failingSystemResolver(hostname: String): List<InetAddress> {
        calledSystem = true
        delay(100)
        throw UnknownHostException("System DNS failed")
    }
    fun systemResolver(hostname: String): List<InetAddress> {
        calledSystem = true
        return listOf(InetAddress.getByAddress(byteArrayOf(1,1,1,1)))
    }
    fun protonResolver(hostname: String): List<InetAddress> {
        calledProton = true
        return listOf(InetAddress.getByAddress(byteArrayOf(2,2,2,2)))
    }

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        testDispatcherProvider = TestDispatcherProvider(testDispatcher)
    }

    @Test
    fun `WHEN outside tunnel THEN is using system dns`() = testScope.runTest {
        val dns = VpnDns(this, inTunnel = MutableStateFlow(false),
            testDispatcherProvider, ::systemResolver, ::protonResolver)
        val result = dns.lookupSuspend("example.com")
        assertTrue(calledSystem)
        assertFalse(calledProton)
        assertEquals(listOf(InetAddress.getByAddress(byteArrayOf(1,1,1,1))), result)
    }

    @Test
    fun `GIVEN in tunnel and fast system DNS lookup THEN proton DNS is not used`() = testScope.runTest {
        val dns = VpnDns(this, inTunnel = MutableStateFlow(true),
            testDispatcherProvider, ::systemResolver, ::protonResolver)
        val result = dns.lookupSuspend("example.com")
        assertTrue(calledSystem)
        assertFalse(calledProton)
        assertEquals(listOf(InetAddress.getByAddress(byteArrayOf(1,1,1,1))), result)
    }

    @Test
    fun `GIVEN in tunnel and slow system DNS lookup THEN proton DNS is used`() = testScope.runTest {
        val dns = VpnDns(this, inTunnel = MutableStateFlow(true),
            testDispatcherProvider, ::longSystemResolver, ::protonResolver)
        val result = dns.lookupSuspend("example.com")
        assertTrue(calledSystem)
        assertTrue(calledProton)
        assertEquals(listOf(InetAddress.getByAddress(byteArrayOf(2,2,2,2))), result)
        assertEquals(SYSTEM_DNS_HEAD_START, currentTime)
    }
}