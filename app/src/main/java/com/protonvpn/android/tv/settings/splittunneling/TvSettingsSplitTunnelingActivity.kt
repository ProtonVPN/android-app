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

package com.protonvpn.android.tv.settings.splittunneling

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.redesign.base.ui.LocalVpnUiDelegate
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

@AndroidEntryPoint
class TvSettingsSplitTunnelingActivity : BaseTvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProtonThemeTv {
                CompositionLocalProvider(LocalVpnUiDelegate provides getVpnUiDelegate()) {
                    Box(
                        contentAlignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val navHostController = rememberNavController()
                        val navigator = remember { TvSplitTunnelingNav(navHostController) }
                        navigator.NavHost(::finish)
                    }
                }
            }
        }
    }
}
