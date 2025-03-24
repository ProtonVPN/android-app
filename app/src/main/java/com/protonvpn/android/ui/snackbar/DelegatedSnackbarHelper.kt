/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.android.ui.snackbar

import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent

/**
 * A helper for displaying delegated snackbars in an activity.
 * See DelegatedSnackbarHelper.
 */
class DelegatedSnackbarHelper(
    private val activity: ComponentActivity,
    view: View,
    private val delegatedSnackManager: DelegatedSnackManager
) : SnackbarHelper(activity.resources, view), DefaultLifecycleObserver {

    // Note: if we need to support more actions this mechanism probably will have to be extended
    // beyond a hardcoded map.
    private val actions = mapOf(
        DelegatedSnackManager.SnackActionType.GOT_IT to { /* Snackbar will dismiss itself. */ }
    )

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        delegatedSnackManager.getPendingSnackOrNull()?.show()
    }

    private fun DelegatedSnackManager.Snack.show() {
        val actionCallback = actions.getOrDefault(action, null)
        val actionString = action?.text?.let { activity.resources.getString(it) }
        showSnack(text, type, length) {
            if (actionCallback != null && actionString != null) setAction(actionString) { actionCallback() }
            anchorView = this@DelegatedSnackbarHelper.anchorView
        }
    }
}
