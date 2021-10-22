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
package com.protonvpn.app

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.protonvpn.android.appconfig.ApiNotification
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationsResponse
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.test.shared.ApiNotificationTestHelper.mockOffer
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import me.proton.core.test.kotlin.CoroutinesTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApiNotificationManagerTests : CoroutinesTest {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @MockK
    private lateinit var appConfig: AppConfig

    var wallClockImpl: () -> Long = { 0 }
    private fun wallClock() = wallClockImpl()
    private val responseObservable = MutableLiveData<ApiNotificationsResponse>(mockResponse())

    private fun mockResponse(vararg items: ApiNotification) =
        ApiNotificationsResponse(arrayOf(*items))

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        wallClockImpl = { 0L }

        every { appConfig.apiNotificationsResponseObservable } returns responseObservable
    }

    @Test
    fun testFiltering() = coroutinesTest {
        val manager = ApiNotificationManager(::wallClock, appConfig)
        responseObservable.value = mockResponse(
            mockOffer("active1", 0L, 1L),
            mockOffer("active2", -1L, 1L),
            mockOffer("future", 1L, 2L),
            mockOffer("just ended", -1L, 0L))

        Assert.assertEquals(listOf("active1", "active2"), manager.activeListFlow.first().map { it.id })
    }

    @Test
    fun testScheduling() = coroutinesTest {
        val manager = ApiNotificationManager(::wallClock, appConfig)
        wallClockImpl = { currentTime }
        val apiTime = currentTime / 1000
        responseObservable.value = mockResponse(
            mockOffer("past", apiTime - 2, apiTime - 1),
            mockOffer("active", apiTime - 1, apiTime + 1),
            mockOffer("future", apiTime + 1, apiTime + 2))

        val observedLists = mutableListOf<List<String>>()
        val collectJob = launch {
            manager.activeListFlow
                .map { list -> list.map { it.id } }
                .toList(observedLists)
        }

        Assert.assertEquals(listOf("active"), manager.activeListFlow.first().map { it.id })
        advanceTimeBy(1500)
        Assert.assertEquals(listOf("future"), manager.activeListFlow.first().map { it.id })
        Assert.assertEquals(listOf(listOf("active"), listOf("future")), observedLists)

        collectJob.cancel()
    }
}
