/*
 * Copyright (c) 2020 Proton Technologies AG
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
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.fragment.app.commit
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.databinding.ActivityTvMainBinding
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.android.tv.TvMainFragment
import com.protonvpn.android.ui.main.AccountViewModel
import com.protonvpn.android.ui.main.MainActivityHelper
import com.protonvpn.android.utils.CountryTools
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TvMainActivity : BaseTvActivity() {

    private val viewModel: TvMainViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()
    private val helper = object : MainActivityHelper(this) {

        override suspend fun onLoginNeeded() {
            clearMainFragment()
            loginLauncher.launch(Unit)
        }

        override suspend fun onReady() = with(supportFragmentManager) {
            if (findFragmentById(R.id.container) == null)
                commit {
                    add(R.id.container, TvMainFragment::class.java, null)
                }
        }
    }

    private lateinit var loginLauncher: ActivityResultLauncher<Unit>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityTvMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        helper.onCreate(accountViewModel)

        loginLauncher = registerForActivityResult(TvLoginActivity.createContract()) {
            if (it.resultCode == Activity.RESULT_CANCELED)
                finish()
        }

        viewModel.selectedCountryFlag.observe(this, Observer {
            updateMapSelection(binding)
        })
        viewModel.connectedCountryFlag.observe(this, Observer {
            updateMapSelection(binding)
        })
        viewModel.mapRegion.observe(this, Observer {
            binding.mapView.setMapRegion(lifecycleScope, it)
        })

        viewModel.onViewInit(lifecycle)
    }

    private fun updateMapSelection(binding: ActivityTvMainBinding) {
        binding.mapView.setSelection(
                CountryTools.codeToMapCountryName[viewModel.selectedCountryFlag.value],
                CountryTools.codeToMapCountryName[viewModel.connectedCountryFlag.value]
        )
    }

    private fun clearMainFragment() = with(supportFragmentManager) {
        commit {
            findFragmentById(R.id.container)?.let {
                remove(it)
            }
        }
    }
}
