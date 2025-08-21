/*
 * Copyright (c) 2021 Proton AG
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

package com.protonvpn.android.ui.main

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.launchAndCollectIn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.presentation.ui.alert.ForceUpdateActivity

abstract class MainActivityHelper(val activity: FragmentActivity) {

    fun onCreate(accountViewModel: AccountViewModel) {
        with(accountViewModel) {
            init(activity)

            onAddAccountClosed = activity::finish

            eventForceUpdate.launchAndCollectIn(activity) {
                activity.startActivity(ForceUpdateActivity(activity, it, Constants.FORCE_UPDATE_URL))
            }

            // RESUMED is needed to avoid duplicate emit (note: distinctUntilChanged is not correct).
            state.flowWithLifecycle(activity.lifecycle, minActiveState = Lifecycle.State.RESUMED)
                .onEach { state -> onStateChange(state) }
                .launchIn(activity.lifecycleScope)
        }
    }

    fun onNewIntent(accountViewModel: AccountViewModel) {
        activity.lifecycleScope.launch {
            onStateChange(accountViewModel.state.value)
        }
    }

    abstract suspend fun onLoginNeeded()
    abstract suspend fun onReady()

    private suspend fun onStateChange(state: AccountViewModel.State) = when (state) {
        AccountViewModel.State.LoginNeeded ->
            onLoginNeeded()
        AccountViewModel.State.Ready ->
            onReady()
        AccountViewModel.State.Initial -> {}
        AccountViewModel.State.Processing -> {}
        AccountViewModel.State.StepNeeded -> {}
        AccountViewModel.State.AutoLoginInProgress -> {}
        is AccountViewModel.State.AutoLoginError -> {}
    }
}
