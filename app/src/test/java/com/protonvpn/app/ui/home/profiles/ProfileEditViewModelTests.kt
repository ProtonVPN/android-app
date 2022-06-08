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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.protonvpn.app.ui.home.profiles

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.ui.home.profiles.ProfileEditViewModel
import com.protonvpn.android.ui.home.profiles.ServerIdSelection
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.test.shared.MockedServers
import com.protonvpn.test.shared.runWhileCollecting
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale

private const val COUNTRY_CODE = "ch"
private const val INVALID_SERVER_ID = "invalid server id"

class ProfileViewModelTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockServerManager: ServerManager
    @MockK
    private lateinit var mockCurrentUser: CurrentUser

    private lateinit var userData: UserData
    private val server = MockedServers.server
    private val country = VpnCountry(COUNTRY_CODE, listOf(server))

    private lateinit var viewModel: ProfileEditViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkObject(CountryTools)
        every { CountryTools.getPreferredLocale() } returns Locale.US

        every { mockServerManager.getVpnExitCountry(any(), any()) } returns null
        every { mockServerManager.getVpnExitCountry(COUNTRY_CODE, false) } returns country
        every { mockServerManager.getServerById(any()) } returns null
        every { mockServerManager.getServerById(server.serverId) } returns server

        userData = UserData.create()
        viewModel = ProfileEditViewModel(mockServerManager, userData, mockCurrentUser)
    }

    @Test
    fun `setServer handles unknown server ID`() = runBlockingTest {
        viewModel.onProfileNameTextChanged("Test profile")
        viewModel.setCountryCode(COUNTRY_CODE)
        viewModel.setServer(ServerIdSelection.Specific(INVALID_SERVER_ID))
        assertTrue(viewModel.serverViewState.first().serverNameValue.isBlank())
    }

    @Test
    fun `validate verifies that selected server exists`() = runBlockingTest {
        every { mockServerManager.getServerById(INVALID_SERVER_ID) } returns server

        viewModel.onProfileNameTextChanged("Test profile")
        viewModel.setCountryCode(COUNTRY_CODE)
        viewModel.setServer(ServerIdSelection.Specific(INVALID_SERVER_ID))

        every { mockServerManager.getServerById(INVALID_SERVER_ID) } returns null

        val somethingWrongEvents = runWhileCollecting(viewModel.eventSomethingWrong) {
            viewModel.saveAndClose()
        }
        assertTrue(somethingWrongEvents.isNotEmpty())
        assertTrue(viewModel.serverViewState.first().serverNameValue.isBlank())
    }

    @Test
    fun `setCountryCode handles unknown country code`() = runBlockingTest {
        viewModel.onProfileNameTextChanged("Test profile")

        val somethingWrongEvents = runWhileCollecting(viewModel.eventSomethingWrong) {
            viewModel.setCountryCode("nonexistent")
        }
        assertTrue(somethingWrongEvents.isNotEmpty())
        val viewState = viewModel.serverViewState.first()
        assertTrue(viewState.countryName.isBlank())
        assertEquals(R.string.profileFastest, viewState.serverNameRes)
    }

    @Test
    fun `setting Secure Core resets selected server to Fastest`() = runBlockingTest {
        viewModel.onProfileNameTextChanged("Test profile")
        viewModel.setCountryCode(COUNTRY_CODE)
        viewModel.setServer(ServerIdSelection.Specific(server.serverId))
        assertEquals(server.serverName, viewModel.serverViewState.first().serverNameValue)

        viewModel.setSecureCore(true)
        val endViewState = viewModel.serverViewState.first()
        assertEquals(R.string.profileFastest, endViewState.serverNameRes)
        assertTrue(endViewState.serverNameValue.isBlank())
    }
}
