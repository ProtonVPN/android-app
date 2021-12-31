/*
 * Copyright (c) 2021 Proton Technologies AG
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
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.databinding.FragmentReportBinding
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.android.models.config.bugreport.InputField
import com.protonvpn.android.models.config.bugreport.TYPE_MULTILINE
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import kotlinx.coroutines.launch
import me.proton.core.presentation.ui.view.ProtonInput
import java.io.Serializable

private const val ARG_CATEGORY = "Category"
private const val MULTILINE_MIN_LINE = 5

class ReportFragment : Fragment(R.layout.fragment_report) {

    private val viewModel: ReportBugActivityViewModel by activityViewModels()
    private val binding by viewBinding(FragmentReportBinding::bind)
    private lateinit var category: Category
    private val inputMap = mutableMapOf<InputField, ProtonInput>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            category = it.getSerializable(ARG_CATEGORY) as Category
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        addDynamicFields()
        viewLifecycleOwner.lifecycleScope.launch {
            editEmail.text = viewModel.getUserEmail()
        }
        buttonReport.setOnClickListener {
            buttonReport.setLoading()
            checkAndPostReport()
        }
        checkboxIncludeLogs.setOnCheckedChangeListener { _, isChecked ->
            layoutMissingLogs.isVisible = !isChecked
        }
    }

    private fun checkAndPostReport() = with(binding) {
        viewModel.prepareAndPostReport(
            emailField = editEmail,
            inputMap = inputMap,
            attachLog = checkboxIncludeLogs.isChecked
        )
        buttonReport.setIdle()
    }

    private fun addDynamicFields() {
        category.inputFields.forEach {
            val input = ProtonInput(requireContext())
            input.labelText = it.label
            input.hintText = it.placeholder
            if (it.type == TYPE_MULTILINE) {
                input.minLines = MULTILINE_MIN_LINE
            }
            input.tag = it.isMandatory
            binding.dynamicContent.addView(input)
            inputMap[it] = input
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(category: Category) = ReportFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_CATEGORY, category as Serializable)
            }
        }
    }
}
