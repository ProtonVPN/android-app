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

package com.protonvpn.app.profiles

import android.app.Activity
import android.content.res.Configuration
import android.net.Uri
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.profiles.usecases.ProfileAutoOpenHandler
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.utils.doesDefaultBrowserSupportEphemeralCustomTabs
import com.protonvpn.android.utils.getEphemeralCustomTabsBrowser
import com.protonvpn.android.utils.openPrivateCustomTab
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ProfileAutoOpenHandlerTests {

    lateinit var testScope: TestScope
    lateinit var newSessionEvent: MutableSharedFlow<Pair<AnyConnectIntent, ConnectTrigger>>
    lateinit var profileIdToAutoOpen: MutableMap<Long, ProfileAutoOpen>
    lateinit var statusFlow: MutableStateFlow<VpnStateMonitor.Status>
    lateinit var foregroundActivityFlow: MutableStateFlow<Activity?>

    lateinit var handler: ProfileAutoOpenHandler

    @Before
    fun setUp() {
        testScope = TestScope()
        newSessionEvent = MutableSharedFlow()
        profileIdToAutoOpen = mutableMapOf()
        statusFlow = MutableStateFlow(VpnStateMonitor.Status(VpnState.Disabled, null))
        foregroundActivityFlow = MutableStateFlow(null)

        mockkStatic(Activity::doesDefaultBrowserSupportEphemeralCustomTabs)
        mockkStatic(Activity::getEphemeralCustomTabsBrowser)
        mockkStatic(Activity::openPrivateCustomTab)

        handler = ProfileAutoOpenHandler(
            testScope,
            newSessionEvent,
            statusFlow,
            { profileIdToAutoOpen[it] },
            foregroundActivityFlow,
            { Configuration.UI_MODE_NIGHT_YES }
        )
    }

    @Test
    fun ifDefaultBrowserSupportsEphemeralCustomTabsItIsUsed() = testScope.runTest {
        val activity = mockk<Activity>()
        val url = mockk<Uri>()
        every { activity.doesDefaultBrowserSupportEphemeralCustomTabs() } returns true
        every { activity.openPrivateCustomTab(url, any(), any()) } returns Unit

        handler.openUrlInPrivateMode(activity, url)
        verify {
            activity.openPrivateCustomTab(url, darkTheme = true, browserPackage = null)
        }
    }

    @Test
    fun whenEphemeralBrowsingNotAvailableWithDefaultBrowserOtherBrowserIsUsed() = testScope.runTest {
        val activity = mockk<Activity>()
        val url = mockk<Uri>()
        every { activity.doesDefaultBrowserSupportEphemeralCustomTabs() } returns false
        every { activity.getEphemeralCustomTabsBrowser(any()) } returns "some.other.browser"
        every { activity.openPrivateCustomTab(url, any(), any()) } returns Unit

        handler.openUrlInPrivateMode(activity, url)
        verify {
            activity.openPrivateCustomTab(url, darkTheme = true, browserPackage = "some.other.browser")
        }
    }

    @Test
    fun whenEphemeralBrowsingNotAvailableItFailsSilently() = testScope.runTest {
        val activity = mockk<Activity>()
        val url = mockk<Uri>()
        every { activity.doesDefaultBrowserSupportEphemeralCustomTabs() } returns false
        every { activity.getEphemeralCustomTabsBrowser(any()) } returns null
        every { activity.openPrivateCustomTab(url, any(), any()) } returns Unit
        handler.openUrlInPrivateMode(activity, url)
        verify(exactly = 0) {
            activity.openPrivateCustomTab(url, any(), any())
        }
    }
}