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

package com.protonvpn.android.ui.planupgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.profiles.usecases.NewProfilesMvpEnabled
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class UpgradeHighlightsCarouselViewModel @Inject constructor(
    newProfilesMvpEnabled: NewProfilesMvpEnabled,
) : ViewModel() {

    private val hasProfiles: Deferred<Boolean> = viewModelScope.async {
        newProfilesMvpEnabled()
    }
    private val gradient = MutableStateFlow<Triple<Int, Int, Int>?>(null)
    val gradientOverride: StateFlow<Triple<Int, Int, Int>?> = gradient

    fun setGradientOverride(override: Triple<Int, Int, Int>?) {
        gradient.value = override
    }

    suspend fun hasProfiles(): Boolean = hasProfiles.await()
}
