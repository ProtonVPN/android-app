/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.tv.upsell

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.upsellBackground
import com.protonvpn.android.base.ui.upsellGradientEnd
import com.protonvpn.android.base.ui.upsellGradientStart
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.tv.buttons.TvTextButton
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

@AndroidEntryPoint
class TvUpsellActivity : BaseTvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ProtonThemeTv {
                val viewModel = hiltViewModel<TvUpsellViewModel>()
                val viewState = viewModel.viewStateFlow.collectAsStateWithLifecycle().value

                if (viewState != null) {
                    TvUpsellLayout(
                        modifier = Modifier.fillMaxSize(),
                        viewState = viewState,
                        onBackClick = { finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvUpsellLayout(
    viewState: TvUpsellViewModel.ViewState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(key1 = Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .background(color = ProtonTheme.colors.upsellBackground)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ProtonTheme.colors.upsellGradientStart,
                        ProtonTheme.colors.upsellGradientEnd,
                        Color.Transparent,
                        Color.Transparent,
                    )
                ),
                alpha = 0.7f,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(fraction = 0.6f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            Image(
                painter = painterResource(id = viewState.imageResId),
                contentDescription = null,
            )

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = viewState.titleResId),
                textAlign = TextAlign.Center,
                style = ProtonTheme.typography.hero,
            )

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = AnnotatedString.fromHtml(
                    htmlString = stringResource(
                        id = viewState.descriptionResId,
                        *viewState.descriptionPlaceholders
                            .toTypedArray()
                            .plus(stringResource(id = viewState.descriptionPlaceholderResId)),
                    )
                ),
                textAlign = TextAlign.Center,
                color = ProtonTheme.colors.textWeak,
                style = ProtonTheme.typography.body1Regular,
            )

            TvTextButton(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .focusRequester(focusRequester),
                text = stringResource(id = R.string.back),
                onClick = onBackClick
            )
        }
    }
}
