/*
 * Copyright (c) 2021 Proton Technologies AG
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
import com.protonvpn.android.utils.launchAndCollectIn
import me.proton.core.presentation.ui.alert.ForceUpdateActivity

abstract class MainActivityHelper(val activity: FragmentActivity) {

    fun onCreate(accountViewModel: AccountViewModel) {
        with(accountViewModel) {
            init(activity)

            onAddAccountClosed(activity::finish)

            eventForceUpdate.launchAndCollectIn(activity) {
                activity.startActivity(ForceUpdateActivity(activity, it))
            }

            state.launchAndCollectIn(activity) { state ->
                when (state) {
                    AccountViewModel.State.LoginNeeded ->
                        onLoginNeeded()
                    AccountViewModel.State.Ready ->
                        onReady()
                    AccountViewModel.State.Initial -> {}
                }
            }
        }
    }

    abstract suspend fun onLoginNeeded()
    abstract suspend fun onReady()
}
