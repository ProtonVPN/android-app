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
package com.protonvpn.android.redesign.recents.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.recents.usecases.DefaultConnItem
import com.protonvpn.android.redesign.recents.usecases.DefaultConnectionViewStateFlow
import com.protonvpn.android.redesign.recents.usecases.SetDefaultConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DefaultConnectionViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    defaultConnectionViewStateFlow: DefaultConnectionViewStateFlow,
    private val setDefaultConnection: SetDefaultConnection,
) : ViewModel() {
    val defaultConnectionViewState = defaultConnectionViewStateFlow
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    fun setNewDefaultConnection(defaultConnectionItem: DefaultConnItem) {
        mainScope.launch {
            val defaultConnection = when (defaultConnectionItem) {
                is DefaultConnItem.DefaultConnItemViewState -> DefaultConnection.Recent(
                    recentId = defaultConnectionItem.id
                )

                is DefaultConnItem.MostRecentItem -> DefaultConnection.LastConnection

                is DefaultConnItem.HeaderSeparator,
                is DefaultConnItem.FastestItem -> DefaultConnection.FastestConnection
            }

            setDefaultConnection(defaultConnection)
        }
    }
}