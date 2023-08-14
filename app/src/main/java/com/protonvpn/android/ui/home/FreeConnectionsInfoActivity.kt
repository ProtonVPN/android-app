/*
 * Copyright (c) 2023 Proton Technologies AG
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
package com.protonvpn.android.ui.home

import android.graphics.Rect
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewOutlineProvider
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.protonvpn.android.R
import com.protonvpn.android.databinding.FreeConnectionsInfoBinding
import com.protonvpn.android.databinding.InfoFreeCountryItemBinding
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesDialogActivity
import com.protonvpn.android.utils.AndroidUtils.launchActivity
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ViewUtils.toPx
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.utils.onClick
import me.proton.core.presentation.utils.viewBinding

@AndroidEntryPoint
class FreeConnectionsInfoActivity : AppCompatActivity() {

    private val viewModel by viewModels<FreeConnectionsInfoViewModel>()
    private val binding by viewBinding(FreeConnectionsInfoBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val freeCountries = viewModel.freeCountriesCodes
        with(binding) {
            locationsHeader.text =
                getString(R.string.free_connections_info_server_locations, freeCountries.size)
            upsellBanner.textTitle.setText(R.string.free_connections_info_banner_text)
            upsellBanner.root.onClick {
                launchActivity<UpgradePlusCountriesDialogActivity>()
            }
            for (country in freeCountries) {
                val item = InfoFreeCountryItemBinding.inflate(
                    layoutInflater, freeCountriesContainer, true)
                item.countryName.text = CountryTools.getFullName(country)
                item.imageFlag.setImageResource(
                    CountryTools.getFlagResource(this@FreeConnectionsInfoActivity, country))
                item.imageFlag.outlineProvider = object: ViewOutlineProvider() {
                    override fun getOutline(view: View?, outline: android.graphics.Outline?) {
                        val dyDp = (FLAG_WIDTH_DP - FLAG_HEIGHT_DP) / 2
                        outline?.setRoundRect(Rect(
                            0, dyDp.toPx(),
                            FLAG_WIDTH_DP.toPx(), (FLAG_WIDTH_DP - dyDp).toPx()
                        ), 4.toPx().toFloat())
                    }
                }
                item.imageFlag.clipToOutline = true
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        const val FLAG_WIDTH_DP = 24
        const val FLAG_HEIGHT_DP = 16
    }
}