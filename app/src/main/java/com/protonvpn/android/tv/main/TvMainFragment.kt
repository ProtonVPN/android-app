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

package com.protonvpn.android.tv.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.R
import com.protonvpn.android.databinding.FragmentTvMainBinding
import com.protonvpn.android.utils.CountryTools
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.utils.viewBinding

private const val MAP_SHOW_DELAY = 500L
private const val MAP_FADE_IN_DURATION = 400L

@AndroidEntryPoint
class TvMainFragment : Fragment(R.layout.fragment_tv_main) {

    private val viewModel: TvMainViewModel by activityViewModels()
    private val binding by viewBinding(FragmentTvMainBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        binding.mapView.init(
            MapRendererConfig(
                background = context.getColor(R.color.tvBackground),
                country = context.getColor(R.color.tvMapCountry),
                border = context.getColor(R.color.tvMapBorder),
                selected = context.getColor(R.color.tvMapSelected),
                connecting = context.getColor(R.color.tvMapSelected),
                connected = context.getColor(R.color.tvMapConnected),
                borderWidth = .2f,
                zoomIndependentBorderWidth = false
            ),
            showDelayMs = MAP_SHOW_DELAY,
            fadeInDurationMs = MAP_FADE_IN_DURATION,
        )
        viewModel.selectedCountryFlag.observe(viewLifecycleOwner) {
            updateMapSelection(binding)
        }
        viewModel.connectedCountryFlag.observe(viewLifecycleOwner) {
            updateMapSelection(binding)
        }
        viewModel.mapRegion.observe(viewLifecycleOwner) {
            binding.mapView.focusRegionInMapBoundsAnimated(viewLifecycleOwner.lifecycleScope, it, minWidth = 0.5f)
        }

        with(binding.versionLabel) {
            alpha = 0f
            @SuppressLint("SetTextI18n")
            text = "ProtonVPN v${BuildConfig.VERSION_NAME}"
            viewModel.showVersion.asLiveData().observe(viewLifecycleOwner) { show ->
                animate().alpha(if (show) 1f else 0f)
            }
        }
        if (savedInstanceState == null) {
            activity?.supportFragmentManager?.commit {
                replace(R.id.home_container, TvHomeFragment())
            }
        }
    }

    private fun updateMapSelection(binding: FragmentTvMainBinding) {
        val selected = CountryTools.codeToMapCountryName[viewModel.selectedCountryFlag.value]
        val connected = CountryTools.codeToMapCountryName[viewModel.connectedCountryFlag.value]
        binding.mapView.setSelection(
            buildList {
                if (connected != null) add(CountryHighlightInfo(connected, CountryHighlight.CONNECTED))
                if (selected != null && selected != connected) add(CountryHighlightInfo(selected, CountryHighlight.SELECTED))
            }
        )
    }
}
