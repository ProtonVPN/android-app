/*
 * Copyright (c) 2020 Proton AG
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
package com.protonvpn.android.tv.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.commit
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.auth.usecase.VpnLogin
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.databinding.ActivityTvMainBinding
import com.protonvpn.android.redesign.app.ui.VpnApp
import com.protonvpn.android.redesign.app.ui.VpnAppViewModel
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.android.tv.login.TvPostLoginFragment
import com.protonvpn.android.ui.main.AccountViewModel
import com.protonvpn.android.ui.main.MainActivityHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@AndroidEntryPoint
class TvMainActivity : BaseTvActivity() {

    private val accountViewModel: AccountViewModel by viewModels()
    private val vpnAppViewModel: VpnAppViewModel by viewModels()
    private var vpnAppStateJob: Job? = null

    @Inject
    lateinit var isTv: IsTvCheck

    @Inject
    lateinit var guestHole: GuestHole

    private val helper = object : MainActivityHelper(this) {

        override suspend fun onLoginNeeded() {
            clearMainFragment()
            loginLauncher.launch(Unit)
        }

        override suspend fun onReady() {
            if (supportFragmentManager.findFragmentById(R.id.container) == null) {
                vpnAppStateJob = vpnAppViewModel.loadingState
                    .flowWithLifecycle(lifecycle)
                    .distinctUntilChanged()
                    .onEach { onAppStateChanged(it) }
                    .launchIn(lifecycleScope)
            }
        }
    }

    private val loginLauncher = registerForActivityResult(TvLoginActivity.createContract()) {
        if (it.resultCode == Activity.RESULT_CANCELED)
            finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityTvMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        helper.onCreate(accountViewModel)

        val isTvIntent = intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        isTv.onUiLaunched(isTvIntent)
    }

    private fun onAppStateChanged(loaderState: VpnAppViewModel.LoaderState?) {
        // The most common path is for the state to change: null -> Loaded.
        // Don't set ant fragment for null to avoid unnecessary work on app start.
        if (loaderState == null)
            return

        if (loaderState == VpnAppViewModel.LoaderState.Loaded) {
            // TODO: release the GH
            //guestHole.releaseNeedGuestHole(VpnLogin.GUEST_HOLE_ID)
        }

        val fragmentClass = if (loaderState == VpnAppViewModel.LoaderState.Loaded) {
            TvMainFragment::class.java
        } else {
            TvPostLoginFragment::class.java
        }
        supportFragmentManager.commit {
            replace(R.id.container, fragmentClass, null)
        }
    }

    private fun clearMainFragment() = with(supportFragmentManager) {
        vpnAppStateJob?.cancel()
        commit {
            findFragmentById(R.id.container)?.let {
                remove(it)
            }
        }
    }
}
