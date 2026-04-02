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

package com.protonvpn.android.tests.auth.ui.sessionfork

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.protonvpn.android.annotations.ProtonVpnTestPreview
import com.protonvpn.android.auth.ui.sessionfork.SessionForkConfirmation
import com.protonvpn.android.auth.ui.sessionfork.SessionForkConfirmationViewModel.ViewState
import com.protonvpn.android.base.ui.ProtonVpnPreview

@ProtonVpnTestPreview
@Composable
fun SessionForkConfirmation_AskConfirmation() {
    SessionForkConfirmationTest(ViewState.AskForkConfirmation(false))
}

@ProtonVpnTestPreview
@Composable
fun SessionForkConfirmation_AskConfirmationLoading() {
    SessionForkConfirmationTest(ViewState.AskForkConfirmation(true))
}

@ProtonVpnTestPreview
@Composable
fun SessionForkConfirmation_Success() {
    SessionForkConfirmationTest(ViewState.Fork.Success(null))
}

@ProtonVpnTestPreview
@Composable
fun SessionForkConfirmation_NetworkError() {
    SessionForkConfirmationTest(ViewState.Fork.Error.Network)
}

@ProtonVpnTestPreview
@Composable
fun SessionForkConfirmation_Expired() {
    SessionForkConfirmationTest(ViewState.Fork.Error.Expired)
}

@ProtonVpnTestPreview
@Composable
fun SessionForkConfirmation_FatalError() {
    SessionForkConfirmationTest(ViewState.Fork.Error.Fatal)
}

@Composable
private fun SessionForkConfirmationTest(state: ViewState) {
    ProtonVpnPreview {
        SessionForkConfirmation(
            viewState = state,
            snackbarHostState = SnackbarHostState(),
            {},
            {},
            {},
            {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}