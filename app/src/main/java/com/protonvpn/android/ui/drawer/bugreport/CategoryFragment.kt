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
package com.protonvpn.android.ui.drawer.bugreport

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.protonvpn.android.R
import com.protonvpn.android.databinding.FragmentCategoryListBinding
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.utils.viewBinding

@AndroidEntryPoint
class CategoryFragment : Fragment(R.layout.fragment_category_list) {

    private val viewModel: ReportBugActivityViewModel by activityViewModels()
    private val binding by viewBinding(FragmentCategoryListBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding.list) {
            layoutManager = LinearLayoutManager(context)
            adapter = CategoryAdapter(viewModel.getCategories()) {
                viewModel.navigateToSuggestions(it)
            }
        }
    }

    companion object {
        @JvmStatic
        fun newInstance() = CategoryFragment()
    }
}
