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

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.tools.screenshot.PreviewTest
import com.protonvpn.android.annotations.ProtonVpnTestPreview
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.ui.settings.IpInputState
import com.protonvpn.android.ui.settings.LabeledItem
import com.protonvpn.android.ui.settings.SplitTunnelingIps

@PreviewTest
@ProtonVpnTestPreview
@Composable
fun SplitTunnelingIpsScreenTest() {
    ProtonVpnPreview {
        val items = buildList {
            repeat(30) { index ->
                add(LabeledItem(id = index.toString(), label = "1.2.3.$index"))
            }
        }
        SplitTunnelingIps(
            mode = SplitTunnelingMode.INCLUDE_ONLY,
            ipInputState = IpInputState({ false }),
            snackbarHostState = remember { SnackbarHostState() },
            ipAddresses = items,
            {}, {}, {}, {}
        )
    }
}
