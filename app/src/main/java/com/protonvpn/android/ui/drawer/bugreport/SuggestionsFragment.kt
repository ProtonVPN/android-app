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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.protonvpn.android.R
import com.protonvpn.android.databinding.FragmentSuggestionsBinding
import com.protonvpn.android.databinding.ItemReportSuggestionBinding
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.android.models.config.bugreport.Suggestion
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.utils.openBrowserLink
import me.proton.core.presentation.utils.viewBinding
import java.io.Serializable

private const val ARG_CATEGORY = "Category"

@AndroidEntryPoint
class SuggestionsFragment : Fragment(R.layout.fragment_suggestions) {

    private val viewModel: ReportBugActivityViewModel by activityViewModels()
    private val binding by viewBinding(FragmentSuggestionsBinding::bind)
    private lateinit var category: Category

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            category = it.getSerializable(ARG_CATEGORY) as Category
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            with(list) {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
                adapter = SuggestionAdapter(
                    category.suggestions
                ) {
                    context.openBrowserLink(it)
                }
             }
            buttonContactUs.setOnClickListener { viewModel.navigateToReport(category) }
            buttonCancel.setOnClickListener { activity?.onBackPressed() }
        }
    }

    class SuggestionAdapter(
        private val values: List<Suggestion>,
        private val onLinkClick: ((String) -> Unit)
    ) : RecyclerView.Adapter<SuggestionAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemReportSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = values[position]
            holder.contentView.text = item.text
            holder.imageLink.isVisible = item.link != null
            item.link?.let { link ->
                holder.imageLink.setOnClickListener {
                    onLinkClick.invoke(link)
                }
            }
        }

        override fun getItemCount(): Int = values.size

        inner class ViewHolder(binding: ItemReportSuggestionBinding) : RecyclerView.ViewHolder(binding.root) {

            val contentView: TextView = binding.content
            val imageLink: View = binding.imageLink
        }

    }
    companion object {

        @JvmStatic
        fun newInstance(category: Category) = SuggestionsFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_CATEGORY, category as Serializable)
            }
        }
    }
}
