/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.tests.api

import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.mocks.TestApiConfig
import com.protonvpn.test.shared.TestUserCountryTelephonyBased
import com.protonvpn.testRules.ProtonHiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import me.proton.core.featureflag.domain.usecase.FetchUnleashTogglesRemote
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.test.assertNotNull

@HiltAndroidTest
class FeatureFlagUserCountryTestsIntegration {

    @get:Rule
    val protonRule = ProtonHiltAndroidRule(this, TestApiConfig.Mocked())

    @Inject
    lateinit var fetchUnleashToggles: FetchUnleashTogglesRemote
    @Inject
    lateinit var serverListUpdaterPrefs: ServerListUpdaterPrefs
    @Inject
    lateinit var telephonyUserCountry: TestUserCountryTelephonyBased

    @Before
    fun setup() {
        protonRule.inject()
    }

    @Test
    fun userCountryPresentInUnleashRequest() {
        serverListUpdaterPrefs.lastKnownCountry = "PL"
        telephonyUserCountry.telephonyCountry = "CH"
        val featuresRequest = refreshUnleashAndGetRequest()
        assertNotNull(featuresRequest)
        assertEquals("userCountry=CH", featuresRequest.requestUrl?.encodedQuery)
    }

    @Test
    fun ipBasedUserCountryIsUsedIfTelephonyIsMissing() {
        serverListUpdaterPrefs.lastKnownCountry = "PL"
        telephonyUserCountry.telephonyCountry = null
        val featuresRequest = refreshUnleashAndGetRequest()
        assertNotNull(featuresRequest)
        assertEquals("userCountry=PL", featuresRequest.requestUrl?.encodedQuery)
    }

    private fun refreshUnleashAndGetRequest(): RecordedRequest {
        runBlocking {
            fetchUnleashToggles(null)
        }
        return protonRule.mockDispatcher.recordedRequests.last {
            it.requestUrl?.encodedPath == "/feature/v2/frontend"
        }
    }
}
