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
import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityRecyclerWithToolbarBinding
import com.protonvpn.android.databinding.ItemServerSelectionBinding
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.ui.HeaderViewHolder
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesDialogActivity
import com.protonvpn.android.utils.AndroidUtils.getFloatRes
import com.protonvpn.android.utils.CountryTools
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import com.xwray.groupie.viewbinding.BindableItem
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CountrySelectionActivity : BaseActivityV2() {

    private val viewModel: CountrySelectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityRecyclerWithToolbarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initToolbarWithUpEnabled(binding.contentAppbar.toolbar)
        val secureCore = getSecureCore(intent)
        setTitle(if (secureCore) R.string.exitCountry else R.string.country)
        initCountryList(binding.recyclerItems, secureCore)
    }

    private fun initCountryList(recyclerItems: RecyclerView, secureCore: Boolean) {
        val layout = LinearLayoutManager(this)
        val upgradeButtonListener = View.OnClickListener {
            startActivity(Intent(this, UpgradePlusCountriesDialogActivity::class.java))
        }
        val sections = viewModel.getCountryGroups(secureCore).mapIndexed { index, group ->
            Section(
                HeaderViewHolder(text = getString(group.label, group.size), itemId = index.toLong()),
                group.countries.map { CountryItemSelectionViewHolder(it, group.isAccessible, upgradeButtonListener) }
            )
        }

        val groupAdapter = GroupAdapter<GroupieViewHolder>().apply {
            addAll(sections)
            setOnItemClickListener { item, _ ->
                if (item is CountryItemSelectionViewHolder) {
                    setResult(
                        Activity.RESULT_OK,
                        Intent().apply { putExtra(COUNTRY_CODE_KEY, item.country.flag) }
                    )
                    finish()
                }
            }
        }
        with(recyclerItems) {
            adapter = groupAdapter
            layoutManager = layout
        }
    }

    private class CountryItemSelectionViewHolder(
        val country: VpnCountry,
        private val isAccessible: Boolean,
        private val upgradeButtonListener: View.OnClickListener
    ) : BindableItem<ItemServerSelectionBinding>() {

        override fun bind(viewBinding: ItemServerSelectionBinding, position: Int) {
            with(viewBinding) {
                textLabel.text = country.countryName
                imageIcon.setImageResource(CountryTools.getFlagResource(root.context, country.flag))
                imageIcon.alpha =
                    if (isAccessible) 1f else root.resources.getFloatRes(R.dimen.inactive_flag_alpha)
                buttonUpgrade.isVisible = !isAccessible
                buttonUpgrade.setOnClickListener(upgradeButtonListener)
                root.isEnabled = isAccessible
            }
        }

        override fun getLayout(): Int = R.layout.item_server_selection
        override fun initializeViewBinding(view: View) = ItemServerSelectionBinding.bind(view)
    }

    companion object {
        fun createContract() = object : ActivityResultContract<Boolean, String?>() {
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
