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

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.commit
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.databinding.ActivityTvMainBinding
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.android.tv.TvMainFragment
import com.protonvpn.android.ui.main.AccountViewModel
import com.protonvpn.android.ui.main.MainActivityHelper
import com.protonvpn.android.utils.CountryTools
import dagger.hilt.android.AndroidEntryPoint

const val MAP_SHOW_DELAY = 500L
const val MAP_FADE_IN_DURATION = 400L

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

        override fun onAssignConnectionNeeded() {
            // Ignore. Handled in TvLoginActivity.
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

        binding.mapView.init(
            MapRendererConfig(
                background = getColor(R.color.tvBackground),
                country = getColor(R.color.tvMapCountry),
                border = getColor(R.color.tvMapBorder),
                selected = getColor(R.color.tvMapSelected),
                connecting = getColor(R.color.tvMapSelected),
                connected = getColor(R.color.tvMapConnected),
                borderWidth = .2f,
                zoomIndependentBorderWidth = false
            ),
            showDelayMs = MAP_SHOW_DELAY,
            fadeInDurationMs = MAP_FADE_IN_DURATION,
        )
        viewModel.selectedCountryFlag.observe(this, Observer {
            updateMapSelection(binding)
        })
        viewModel.connectedCountryFlag.observe(this, Observer {
            updateMapSelection(binding)
        })
        viewModel.mapRegion.observe(this, Observer {
            binding.mapView.focusRegionInMapBoundsAnimated(lifecycleScope, it, minWidth = 0.5f)
        })

        with(binding.versionLabel) {
            alpha = 0f
            @SuppressLint("SetTextI18n")
            text = "ProtonVPN v${BuildConfig.VERSION_NAME}"
            viewModel.showVersion.asLiveData().observe(this@TvMainActivity, Observer { show ->
                animate().alpha(if (show) 1f else 0f)
            })
        }
    }

    private fun updateMapSelection(binding: ActivityTvMainBinding) {
        val selected = CountryTools.codeToMapCountryName[viewModel.selectedCountryFlag.value]
        val connected = CountryTools.codeToMapCountryName[viewModel.connectedCountryFlag.value]
        binding.mapView.setSelection(
            buildList {
                if (connected != null) add(CountryHighlightInfo(connected, CountryHighlight.CONNECTED))
                if (selected != null && selected != connected) add(CountryHighlightInfo(selected, CountryHighlight.SELECTED))
            }
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
