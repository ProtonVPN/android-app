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

package com.protonvpn.app.vpn

import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.vpn.ServerAvailabilityCheck
import com.protonvpn.android.vpn.ServerPing
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

private const val GOOD_IP = "7.7.7.7"
private const val BAD_IP = "8.8.8.8"
private const val TIMEOUT = 5000
fun isGoodPort(port: Int) = port < 10

@OptIn(ExperimentalCoroutinesApi::class)
class ServerAvailabilityCheckTests {

    @RelaxedMockK
    private lateinit var serverPing: ServerPing

    private lateinit var serverAvailabilityCheck: ServerAvailabilityCheck

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        serverAvailabilityCheck = ServerAvailabilityCheck(serverPing)
        val portCapture = slot<Int>()
        coEvery { serverPing.ping(GOOD_IP, capture(portCapture), any(), any()) } answers {
            isGoodPort(portCapture.captured)
        }
        coEvery { serverPing.ping(BAD_IP, any(), any(), any()) } coAnswers {
            delay(2L * TIMEOUT)
            false
        }
    }

    @Test
    fun `empty ping`() = runTest {
        assertTrue(serverAvailabilityCheck.pingInParallel(emptyMap(), true).isEmpty())
    }

    @Test
    fun `ping returns only only accessible IPs and ports`() = runTest {
        val destinations = mapOf(
            TransmissionProtocol.UDP to ServerAvailabilityCheck.Destination(GOOD_IP, listOf(1, 100), "key"),
            TransmissionProtocol.TCP to ServerAvailabilityCheck.Destination(BAD_IP, listOf(2, 200), null))
        val result = serverAvailabilityCheck.pingInParallel(destinations, true)
        assertEquals(mapOf(TransmissionProtocol.UDP to ServerAvailabilityCheck.Destination(GOOD_IP, listOf(1), "key")), result)
    }

    @Test
    fun `TLS is not pinged separately if destination same as TCP`() = runTest {
        val destination = ServerAvailabilityCheck.Destination(GOOD_IP, listOf(1, 100), "key")
        val resultDestination = ServerAvailabilityCheck.Destination(GOOD_IP, listOf(1), "key")
        val destinations = mapOf(
            TransmissionProtocol.TCP to destination,
            TransmissionProtocol.TLS to destination)
        val result = serverAvailabilityCheck.pingInParallel(destinations, true)
        assertEquals(
            mapOf(TransmissionProtocol.TCP to resultDestination, TransmissionProtocol.TLS to resultDestination),
            result)
        coVerify(exactly = 1) { serverPing.ping(GOOD_IP, 1, any(), any()) }
    }

    @Test
    fun `test pinging happens in parallel`() = runTest {
        coEvery { serverPing.ping(any(), any(), any(), any()) } coAnswers {
            delay(1000)
            true
        }
        val destination = ServerAvailabilityCheck.Destination(GOOD_IP, listOf(1, 100), "key")
        val destinations = mapOf(
            TransmissionProtocol.UDP to destination,
            TransmissionProtocol.TCP to destination)
        val before = currentTime
        serverAvailabilityCheck.pingInParallel(destinations, true)
        assertEquals(1000, currentTime - before)
    }

    @Test
    fun `when waitForAll is false only fastest port per destination is returned`() = runTest {
        val portNum = slot<Int>()
        coEvery { serverPing.ping(any(), capture(portNum), any(), any()) } coAnswers {
            delay(1000L * portNum.captured)
            true
        }
        val before = currentTime
        val destinations = mapOf(
            TransmissionProtocol.TCP to ServerAvailabilityCheck.Destination(GOOD_IP, listOf(3, 1, 2), "key"),
            TransmissionProtocol.TLS to ServerAvailabilityCheck.Destination(GOOD_IP, listOf(5, 7, 4), "key"))
        val result = serverAvailabilityCheck.pingInParallel(destinations, false)
        assertEquals(4000, currentTime - before)
        assertEquals(mapOf(
            TransmissionProtocol.TCP to ServerAvailabilityCheck.Destination(GOOD_IP, listOf(1), "key"),
            TransmissionProtocol.TLS to ServerAvailabilityCheck.Destination(GOOD_IP, listOf(4), "key")), result)
    }

    @Test
    fun `UDP pings fail without the key`() = runTest {
        val destinations = mapOf(
            TransmissionProtocol.UDP to ServerAvailabilityCheck.Destination(GOOD_IP, listOf(1), null),
            TransmissionProtocol.TCP to ServerAvailabilityCheck.Destination(GOOD_IP, listOf(2), null))
        val result = serverAvailabilityCheck.pingInParallel(destinations, true)
        assertEquals(
            mapOf(TransmissionProtocol.TCP to ServerAvailabilityCheck.Destination(GOOD_IP, listOf(2), null)),
            result)
    }
}
