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

package com.protonvpn.android.redesign.main_screen.ui

import androidx.lifecycle.ViewModel
import com.protonvpn.android.redesign.home_screen.ui.ShowcaseRecents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

@HiltViewModel
class MainScreenViewModel @Inject constructor(): ViewModel() {

    private val _eventCollapseRecents = MutableSharedFlow<ShowcaseRecents>(replay = 1)
    val eventCollapseRecents: SharedFlow<ShowcaseRecents> = _eventCollapseRecents

    fun requestCollapseRecents(showcaseRecents: ShowcaseRecents) {
        _eventCollapseRecents.tryEmit(showcaseRecents)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeEventCollapseRecents() {
        _eventCollapseRecents.resetReplayCache()
    }
}
