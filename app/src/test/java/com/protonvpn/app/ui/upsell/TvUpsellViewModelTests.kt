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

package com.protonvpn.app.ui.upsell

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.protonvpn.android.R
import com.protonvpn.android.models.features.PaidFeature
import com.protonvpn.android.tv.upsell.TvUpsellViewModel
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TvUpsellViewModelTests {

    private lateinit var serverListUpdaterPrefs: ServerListUpdaterPrefs

    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher(scheduler = TestCoroutineScheduler())

        Dispatchers.setMain(dispatcher = testDispatcher)

        testScope = TestScope(context = testDispatcher)

        serverListUpdaterPrefs = ServerListUpdaterPrefs(
            prefsProvider = MockSharedPreferencesProvider(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `GIVEN paid feature WHEN observing view state THEN associated view state is emitted`() = testScope.runTest {
        serverListUpdaterPrefs.vpnCountryCount = 126
        serverListUpdaterPrefs.vpnServerCount = 15_187

        listOf(
            PaidFeature.AllCountries to TvUpsellViewModel.ViewState(
                imageResId = R.drawable.worldwide_coverage_tv,
                titleResId = R.string.upsell_tv_all_countries_title,
                descriptionResId = R.string.upsell_tv_all_countries_description,
                descriptionPlaceholders = listOf("15187", "126"),
            ),
            PaidFeature.CustomDns to TvUpsellViewModel.ViewState(
                imageResId = R.drawable.customisation_tv,
                titleResId = R.string.upsell_tv_customization_title,
                descriptionResId = R.string.upsell_tv_customization_description,
            ),
            PaidFeature.LanConnections to TvUpsellViewModel.ViewState(
                imageResId = R.drawable.customisation_tv,
                titleResId = R.string.upsell_tv_customization_title,
                descriptionResId = R.string.upsell_tv_customization_description,
            ),
            PaidFeature.NetShield to TvUpsellViewModel.ViewState(
                imageResId = R.drawable.netshield_tv,
                titleResId = R.string.upsell_tv_netshield_title,
                descriptionResId = R.string.upsell_tv_netshield_description,
            ),
            PaidFeature.SplitTunneling to TvUpsellViewModel.ViewState(
                imageResId = R.drawable.split_tunneling_tv,
                titleResId = R.string.upsell_tv_split_tunneling_title,
                descriptionResId = R.string.upsell_tv_split_tunneling_description,
            ),
        ).forEach { (paidFeature, expectedViewState) ->
            val viewModel = createViewModel(paidFeature = paidFeature)

            viewModel.viewStateFlow.test {
                val viewState = awaitItem()

                assertEquals(
                    expected = expectedViewState,
                    actual = viewState,
                    message = "ViewState does not match for paid feature: $paidFeature",
                )
            }
        }
    }

    private fun createViewModel(paidFeature: PaidFeature): TvUpsellViewModel {
        val savedStateHandle = SavedStateHandle().apply {
            this["paid_feature_key"] = paidFeature
        }

        return TvUpsellViewModel(
            savedStateHandle = savedStateHandle,
            serverListUpdaterPrefs = serverListUpdaterPrefs,
        )
    }

}
