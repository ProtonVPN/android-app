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

package com.protonvpn.android.tv.login

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.tv.main.TvMainActivity
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

@AndroidEntryPoint
class TvQrLoginActivity : BaseTvActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ProtonThemeTv {
                val viewModel: TvQrLoginViewModel = hiltViewModel()
                val viewState by viewModel.viewState.collectAsStateWithLifecycle()
                if (viewState is TvQrLoginViewModel.ViewState.Login.Success) {
                    LaunchedEffect(Unit) {
                        onLoginSuccess()
                    }
                }
                TvQrLoginScreen(
                    viewState = viewState,
                    onCreateNewCode = viewModel::createNewCode,
                    now = viewModel::now,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ProtonTheme.colors.backgroundNorm)
                        .paint(
                            painterResource(R.drawable.bg_gradient_glow),
                            sizeToIntrinsics = false,
                            alignment = Alignment.TopCenter,
                        ),
                )
            }
        }
    }

    private fun onLoginSuccess() {
        // This is what the legacy TvLoginActivity does. It doesn't look great, most likely works
        // around some navigation issues. My guess is that TvMainActivity may be there in the
        // background but it's not guaranteed.
        setResult(Activity.RESULT_OK)
        startActivity(Intent(this, TvMainActivity::class.java))
        finish()
    }

    companion object {
        fun createContract() = object : ActivityResultContract<Unit, ActivityResult>() {
            override fun createIntent(context: Context, input: Unit) =
                Intent(context, TvQrLoginActivity::class.java)
            override fun parseResult(resultCode: Int, intent: Intent?) = ActivityResult(resultCode, null)
        }
    }
}