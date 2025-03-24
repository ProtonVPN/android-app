/*
 * Copyright (c) 2021 Proton AG
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

package com.protonvpn.android.tv.detailed

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.protonvpn.android.R
import com.protonvpn.android.databinding.FragmentTvServerListScreenBinding
import com.protonvpn.android.utils.CountryTools

class TvServerListScreenFragment : Fragment(R.layout.fragment_tv_server_list_screen) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.commit {
                add(R.id.container, TvServerListFragment::class.java, arguments)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentTvServerListScreenBinding.bind(view)

        val country = requireArguments()[EXTRA_COUNTRY] as String
        binding.countryName.text = CountryTools.getFullName(country)
        binding.flag.setImageResource(CountryTools.getFlagResource(requireContext(), country))
        binding.flag.transitionName = CountryDetailFragment.transitionNameForCountry(country)
    }

    companion object {
        const val EXTRA_COUNTRY = "EXTRA_COUNTRY"
    }
}
