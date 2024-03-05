/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.tests.api

import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.TestUserCountryProvider
import com.protonvpn.testRules.ProtonHiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import me.proton.core.featureflag.domain.usecase.FetchUnleashTogglesRemote
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.test.assertNotNull

@HiltAndroidTest
class FeatureFlagUserCountryTests {

    @get:Rule
    val protonRule = ProtonHiltAndroidRule(this, TestApiConfig.Mocked())

    @Inject
    lateinit var fetchUnleashToggles: FetchUnleashTogglesRemote
    @Inject
    lateinit var userCountryProvider: TestUserCountryProvider

    @Before
    fun setup() {
        protonRule.inject()
    }

    @Test
    fun userCountryPresentInUnleashRequest() {
        userCountryProvider.ipCountry = "CH"
        runBlocking {
            fetchUnleashToggles(null)
        }
        val featuresRequest = protonRule.mockDispatcher.recordedRequests.last {
            it.requestUrl?.encodedPath == "/feature/v2/frontend"
        }
        assertNotNull(featuresRequest)
        assertEquals("userCountry=CH", featuresRequest.requestUrl?.encodedQuery)
    }
}
