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

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.commit
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivityTvMainBinding
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.android.tv.TvMainFragment
import com.protonvpn.android.ui.home.TvHomeViewModel
import com.protonvpn.android.utils.CountryTools
import javax.inject.Inject

@ContentLayout(R.layout.activity_tv_main)
class TvMainActivity : BaseTvActivity<ActivityTvMainBinding>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private val viewModel: TvHomeViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add(R.id.container, TvMainFragment::class.java, null)
            }
        }
        viewModel.selectedCountry.observe(this, Observer {
            updateMapSelection()
        })
        viewModel.connectedCountryFlag.observe(this, Observer {
            updateMapSelection()
        })
        viewModel.mapRegion.observe(this, Observer {
            binding.mapView.setMapRegion(lifecycleScope, it)
        })

        viewModel.logoutEvent.observe(this) {
            finish()
            startActivity(Intent(this, TvLoginActivity::class.java))
        }

        viewModel.onViewInit(lifecycle)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        viewModel.resetMap()
    }

    private fun updateMapSelection() {
        binding.mapView.setSelection(
                CountryTools.codeToMapCountryName[viewModel.selectedCountry.value?.flag],
                CountryTools.codeToMapCountryName[viewModel.connectedCountryFlag.value]
        )
    }
}
