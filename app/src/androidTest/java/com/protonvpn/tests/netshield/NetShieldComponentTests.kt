/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.tests.netshield

import androidx.compose.runtime.collectAsState
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.netshield.NetShieldView
import com.protonvpn.android.netshield.NetShieldViewState
import kotlinx.coroutines.flow.MutableStateFlow
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.ui.compose.FusionComposeTest
import org.junit.Before
import org.junit.Test

class NetShieldComponentTests : FusionComposeTest() {

    private lateinit var netShieldStateFlow: MutableStateFlow<NetShieldViewState>

    @Before
    fun setup() {
        netShieldStateFlow = MutableStateFlow(
            NetShieldViewState.NetShieldState(
                NetShieldProtocol.ENABLED,
                NetShieldStats(1, 2, 0)
            )
        )
        withContent {
            NetShieldView(
                netShieldStateFlow.collectAsState().value as NetShieldViewState.NetShieldState,
                onNavigateToSubsetting = {}
            )
        }
    }

    @Test
    fun savedBytesExpressedInKbAndMb() {
        netShieldStateFlow.value = NetShieldViewState.NetShieldState(
            NetShieldProtocol.ENABLED,
            NetShieldStats(1, 2, 10_000)
        )

        node.useUnmergedTree()
            .hasAncestor(node.withTag("bandwidthSaved"))
            .withTag("value")
            .assertContainsText("9.77 KB")

        netShieldStateFlow.value = NetShieldViewState.NetShieldState(
            NetShieldProtocol.ENABLED,
            NetShieldStats(1, 2, 10_000_000)
        )

        node.useUnmergedTree()
            .hasAncestor(node.withTag("bandwidthSaved"))
            .withTag("value")
            .assertContainsText("9.54 MB")
    }
}
