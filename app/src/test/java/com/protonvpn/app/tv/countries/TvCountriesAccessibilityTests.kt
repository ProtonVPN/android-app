/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.app.tv.countries

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.tv.countries.ui.CountryConnectMenu
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuOption
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuOption.Type
import com.protonvpn.android.tv.countries.ui.CountryConnectMenuUiState
import com.protonvpn.android.tv.countries.ui.CountryGridTile
import com.protonvpn.android.tv.countries.ui.CountryGridUiItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TvCountriesAccessibilityTests {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `available country tile connects on click and shows connection options on long click`() {
        setTile(tileItem())
        assertEquals(string(R.string.accessibility_action_connect), clickLabel("Switzerland"))
        assertEquals(string(R.string.accessibility_action_show_connection_options), longClickLabel("Switzerland"))
    }

    @Test
    fun `unavailable country tile upgrades on click`() {
        setTile(tileItem(available = false))
        assertEquals(string(R.string.accessibility_action_upgrade), clickLabel("Switzerland"))
    }

    @Test
    fun `country tile in maintenance has no click label`() {
        setTile(tileItem(status = CountryGridUiItem.Status.InMaintenance))
        assertNull(clickLabel("Switzerland"))
    }

    @Test
    fun `menu options are labeled with their click actions`() {
        val options = listOf(
            menuOption(Type.Fastest, connected = true),
            menuOption(Type.Random),
            menuOption(city("Geneva"), available = false),
            menuOption(city("Zurich")),
            menuOption(city("Bern"), inMaintenance = true),
        )
        composeRule.setContent {
            ProtonVpnTvPreview {
                CountryConnectMenu(
                    CountryConnectMenuUiState(CountryId("CH"), "Switzerland", R.drawable.flag_tv_ch, options),
                    onOptionClick = {},
                )
            }
        }

        assertEquals(string(R.string.accessibility_action_disconnect), clickLabel("Fastest"))
        assertNull(clickLabel("Random"))
        assertEquals(string(R.string.accessibility_action_upgrade), clickLabel("Geneva"))
        assertEquals(string(R.string.accessibility_action_connect), clickLabel("Zurich"))
        assertNull(clickLabel("Bern"))
        // The label is merged with the Surface's own click action.
        assertTrue(composeRule.onNodeWithText("Zurich").fetchSemanticsNode().config[SemanticsActions.OnClick].action != null)
        assertTrue(
            SemanticsProperties.Heading in composeRule.onNodeWithText("Switzerland", useUnmergedTree = true)
                .fetchSemanticsNode().config
        )
    }

    private fun setTile(item: CountryGridUiItem) {
        composeRule.setContent {
            ProtonVpnTvPreview {
                CountryGridTile(item, onClick = {}, onLongClick = {})
            }
        }
    }

    private fun clickLabel(text: String): String? =
        composeRule.onNodeWithText(text).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label

    private fun longClickLabel(text: String): String? =
        composeRule.onNodeWithText(text).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnLongClick)?.label

    private fun string(@StringRes id: Int) = context.getString(id)

    private fun tileItem(
        available: Boolean = true,
        status: CountryGridUiItem.Status = CountryGridUiItem.Status.Regular,
    ) = CountryGridUiItem(
        countryId = CountryId("CH"),
        countryName = "Switzerland",
        flagResource = R.drawable.flag_tv_ch,
        status = status,
        available = available,
        tier = if (available) 0 else 2,
        cityStateCount = 1,
        hasStates = false,
    )

    private fun city(name: String) = Type.CityState(CityStateId(name, isState = false), name)

    private fun menuOption(
        type: Type,
        available: Boolean = true,
        inMaintenance: Boolean = false,
        connected: Boolean = false,
    ) = CountryConnectMenuOption(type, available, inMaintenance, connected)
}
