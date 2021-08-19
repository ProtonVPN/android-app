/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A view model base class for SaveableSettingsActivity.
 */
abstract class SaveableSettingsViewModel : ViewModel() {

    val eventConfirmDiscardChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val eventFinishActivity = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val eventGoBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun saveAndClose() {
        val isValid = validate()
        if (isValid) {
            val hasAnyChanges = hasUnsavedChanges()
            saveChanges()
            eventFinishActivity.tryEmit(hasAnyChanges)
        }
    }

    fun onGoBack() {
        if (hasUnsavedChanges()) {
            eventConfirmDiscardChanges.tryEmit(Unit)
        } else {
            eventGoBack.tryEmit(Unit)
        }
    }

    fun onDiscardChanges() {
        eventGoBack.tryEmit(Unit)
    }

    /**
     * Save the current state.
     *
     * It is called only after validate() returns true so the state must be valid.
     */
    protected abstract fun saveChanges()
    protected abstract fun hasUnsavedChanges(): Boolean

    /**
     * Validate the current input.
     *
     * Return true if data may be saved.
     */
    protected open fun validate(): Boolean = true
}
