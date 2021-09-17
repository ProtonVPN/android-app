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
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivityRecyclerWithToolbarBinding
import com.protonvpn.android.databinding.ItemServerSelectionBinding
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.ui.HeaderViewHolder
import com.protonvpn.android.utils.AndroidUtils.getFloatRes
import com.protonvpn.android.utils.CountryTools
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import com.xwray.groupie.databinding.BindableItem
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
@ContentLayout(R.layout.activity_recycler_with_toolbar)
class CountrySelectionActivity : BaseActivityV2<ActivityRecyclerWithToolbarBinding, CountrySelectionViewModel>() {

    override fun initViewModel() {
        viewModel =
            ViewModelProvider(this).get(CountrySelectionViewModel::class.java)
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
        val sections = viewModel.getCountryGroups(secureCore).mapIndexed { index, group ->
            Section(
                HeaderViewHolder(text = getString(group.label, group.size), itemId = index.toLong()),
                group.countries.map { CountryItemSelectionViewHolder(it, group.isAccessible) } )
        }

        val groupAdapter = GroupAdapter<GroupieViewHolder>().apply {
            addAll(sections)
            setOnItemClickListener { item, _ ->
                val country = (item as CountryItemSelectionViewHolder).country
                setResult(
                    Activity.RESULT_OK,
                    Intent().apply { putExtra(COUNTRY_CODE_KEY, country.flag) }
                )
                finish()
            }
        }
        with(binding.recyclerItems) {
            adapter = groupAdapter
            layoutManager = layout
        }
    }

    private class CountryItemSelectionViewHolder(
        val country: VpnCountry,
        private val isAccessible: Boolean
    ) : BindableItem<ItemServerSelectionBinding>() {

        override fun bind(viewBinding: ItemServerSelectionBinding, position: Int) {
            with(viewBinding) {
                textLabel.text = country.countryName
                imageIcon.setImageResource(CountryTools.getFlagResource(root.context, country.flag))
                imageIcon.alpha =
                    if (isAccessible) 1f else root.resources.getFloatRes(R.dimen.inactive_flag_alpha)
                root.isEnabled = isAccessible
            }
        }

        override fun getLayout(): Int = R.layout.item_server_selection
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
