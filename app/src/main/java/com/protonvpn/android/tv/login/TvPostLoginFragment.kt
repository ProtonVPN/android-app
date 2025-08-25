/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.tv.login

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.redesign.app.ui.FullScreenLoading
import com.protonvpn.android.redesign.app.ui.VpnAppViewModel
import com.protonvpn.android.tv.ui.onFocusLost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv
import javax.inject.Inject

@AndroidEntryPoint
class TvPostLoginFragment : Fragment() {

    @Inject
    lateinit var mainScope: CoroutineScope

    @Inject
    lateinit var logout: Logout

    private val viewModel: VpnAppViewModel by viewModels( ownerProducer = { requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ProtonThemeTv {
                    val state = viewModel.loadingState.collectAsStateWithLifecycle().value
                    if (state == null) {
                        Box(Modifier.fillMaxSize())
                    } else {
                        TvPostLoginScreen(
                            state = state,
                            signOut = { mainScope.launch { logout() } },
                            Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvPostLoginScreen(
    state: VpnAppViewModel.LoaderState,
    signOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            VpnAppViewModel.LoaderState.Loaded -> Unit
            VpnAppViewModel.LoaderState.Loading -> FullScreenLoading()
            is VpnAppViewModel.LoaderState.Error -> ErrorState(state, signOut)
        }
    }
}

@Composable
private fun ErrorState(
    state: VpnAppViewModel.LoaderState.Error,
    signOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Remove when implemented in Compose TV: https://issuetracker.google.com/issues/268268856
    // (can't use the suggestion from issuetracker with `clickable` modifier because it breaks the `Surface`
    // selection).
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val focusSoundModifier = Modifier.onFocusLost {
        audioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Image(
            painterResource(R.drawable.globe_error_tv),
            contentDescription = null,
        )
        Text(
            stringResource(state.titleResId),
            style = ProtonTheme.typography.headline
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(state.descriptionResId),
            style = ProtonTheme.typography.body2Regular,
            color = ProtonTheme.colors.textWeak
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                state.retryAction,
                modifier = focusSoundModifier.focusRequester(focusRequester),
            ) {
                Text(stringResource(R.string.no_connections_action_refresh))
            }
            Button(
                onClick = signOut,
                modifier = focusSoundModifier,
            ) {
                Text(stringResource(R.string.no_connections_action_sign_out))
            }
        }
    }
}
