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

package com.protonvpn.android.tests.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.protonvpn.android.annotations.ProtonVpnTestPreview
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.ui.settings.LabeledItem
import com.protonvpn.android.ui.settings.SplitTunnelingApps
import com.protonvpn.android.ui.settings.SplitTunnelingAppsViewModelHelper
import me.proton.core.presentation.R as CoreR

private val apps1 = listOf(
    LabeledItem("1", "App 1", CoreR.drawable.ic_proton_brand_proton_vpn),
    LabeledItem("2", "Calendar", CoreR.drawable.ic_proton_brand_proton_calendar),
    LabeledItem("3", "Mail", CoreR.drawable.ic_proton_brand_proton_mail),
)

private val apps2 = listOf(
    LabeledItem("4", "Pass", CoreR.drawable.ic_proton_brand_proton_pass),
    LabeledItem("5", "Drive", CoreR.drawable.ic_proton_brand_proton_drive),
)

private val apps3 = listOf(
    LabeledItem("6", "App 1", CoreR.drawable.ic_proton_brand_android),
    LabeledItem("7", "App 2", CoreR.drawable.ic_proton_paper_clip),
    LabeledItem("8", "App 3", CoreR.drawable.ic_proton_clock_filled),
)

@PreviewTest
@ProtonVpnTestPreview
@Composable
fun SplitTunnelingAppsScreen_Empty_RegularAppsOnly_Test() {
    ProtonVpnPreview {
        val state = SplitTunnelingAppsViewModelHelper.ViewState.Content(
            selectedApps = emptyList(),
            availableRegularApps = apps1 + apps2,
            availableSystemApps = SplitTunnelingAppsViewModelHelper.SystemAppsState.NotLoaded(emptyList()),
        )
        SplitTunnelingApps(
            mode = SplitTunnelingMode.EXCLUDE_ONLY,
            viewState = state,
            {}, {}, {}, {}, {}
        )
    }
}

@PreviewTest
@ProtonVpnTestPreview
@Composable
fun SplitTunnelingAppsScreen_Included_RegularAndSystemApps_Test() {
    ProtonVpnPreview {
        val state = SplitTunnelingAppsViewModelHelper.ViewState.Content(
            selectedApps = apps1,
            availableRegularApps = apps2,
            availableSystemApps = SplitTunnelingAppsViewModelHelper.SystemAppsState.Content(apps3),
        )
        SplitTunnelingApps(
            mode = SplitTunnelingMode.INCLUDE_ONLY,
            viewState = state,
            {}, {}, {}, {}, {}
        )
    }
}
