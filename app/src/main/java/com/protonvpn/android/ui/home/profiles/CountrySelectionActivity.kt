/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui.home.profiles

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivityCountrySelectionBinding
import com.protonvpn.android.databinding.ItemServerSelectionBinding
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.utils.CountryTools
import javax.inject.Inject

@ContentLayout(R.layout.activity_country_selection)
class CountrySelectionActivity : BaseActivityV2<ActivityCountrySelectionBinding, CountrySelectionViewModel>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun initViewModel() {
        viewModel =
            ViewModelProvider(this, viewModelFactory).get(CountrySelectionViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbarWithUpEnabled(binding.contentAppbar.toolbar)
        val secureCore = getSecureCore(intent)
        setTitle(if (secureCore) R.string.exitCountry else R.string.country)
        initCountryList(secureCore)
    }

    private fun initCountryList(secureCore: Boolean) {
        val layout = LinearLayoutManager(this)
        val countriesAdapter =
            CountriesAdapter(viewModel.getCountryItems(secureCore)) { selectedCountry ->
                setResult(
                    Activity.RESULT_OK,
                    Intent().apply { putExtra(COUNTRY_CODE_KEY, selectedCountry.flag) }
                )
                finish()
            }

        with(binding.recyclerCountries) {
            adapter = countriesAdapter
            layoutManager = layout
        }
    }

    private class CountryViewHolder(val views: ItemServerSelectionBinding) : RecyclerView.ViewHolder(views.root)

    private class CountriesAdapter(
        private val countries: List<VpnCountry>,
        private val onSelected: (VpnCountry) -> Unit
    ) : RecyclerView.Adapter<CountryViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CountryViewHolder =
            CountryViewHolder(ItemServerSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false))


        override fun onBindViewHolder(holder: CountryViewHolder, position: Int) {
            with(holder.views) {
                val country = countries[position]
                textLabel.text = country.countryName
                imageIcon.setImageResource(CountryTools.getFlagResource(root.context, country.flag))
                root.setOnClickListener {
                    onSelected(country)
                }
            }
        }

        override fun getItemCount(): Int = countries.size
    }

    companion object {
        fun createContract() = object : ActivityResultContract<Boolean, String>() {
            override fun createIntent(context: Context, secureCore: Boolean): Intent =
                Intent(context, CountrySelectionActivity::class.java).apply {
                    putExtra(SECURE_CORE_KEY, secureCore)
                }

            override fun parseResult(resultCode: Int, intent: Intent?): String? {
                return if (resultCode == Activity.RESULT_OK)
                    intent?.getStringExtra(COUNTRY_CODE_KEY)
                else
                    null
            }
        }

        private fun getSecureCore(intent: Intent) = intent.getBooleanExtra(SECURE_CORE_KEY, false)

        private const val SECURE_CORE_KEY = "secureCore"
        private const val COUNTRY_CODE_KEY = "countryCode"
    }
}
