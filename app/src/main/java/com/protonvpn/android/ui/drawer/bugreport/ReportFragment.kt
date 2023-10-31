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
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.databinding.FragmentReportBinding
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.android.models.config.bugreport.InputField
import com.protonvpn.android.models.config.bugreport.TYPE_DROPDOWN
import com.protonvpn.android.models.config.bugreport.TYPE_MULTILINE
import com.protonvpn.android.models.config.bugreport.TYPE_SINGLELINE
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import kotlinx.coroutines.launch
import me.proton.core.presentation.ui.view.ProtonAutoCompleteInput
import me.proton.core.presentation.ui.view.ProtonInput
import java.io.Serializable

private const val ARG_CATEGORY = "Category"
private const val MULTILINE_MIN_LINE = 5

class ReportFragment : Fragment(R.layout.fragment_report) {

    private val viewModel: ReportBugActivityViewModel by activityViewModels()
    private val binding by viewBinding(FragmentReportBinding::bind)
    private lateinit var category: Category
    private val inputMap = mutableMapOf<InputField, ReportBugActivityViewModel.DynamicInputUI>()

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
            viewModel.prepareAndPostReport(
                emailField = editEmail,
                category = category,
                dynamicInputMap = inputMap,
                attachLog = checkboxIncludeLogs.isChecked
            )
        }
        checkboxIncludeLogs.setOnCheckedChangeListener { _, isChecked ->
            layoutMissingLogs.isVisible = !isChecked
        }
        viewModel.state.observe(viewLifecycleOwner, Observer {
            if (it is ReportBugActivityViewModel.ViewState.SubmittingReport) {
                buttonReport.setLoading()
            } else {
                buttonReport.setIdle()
            }
        })
    }

    private fun addDynamicFields() {
        category.inputFields.forEach { inputField ->
            when (inputField.type) {
                TYPE_SINGLELINE -> addTextInput(inputField, multiLine = false)
                TYPE_MULTILINE -> addTextInput(inputField, multiLine = true)
                TYPE_DROPDOWN -> addDropDown(inputField)
                else -> ProtonLogger.logCustom(
                    LogCategory.API, "Unsupported type: " + inputField.type + " for dynamic bug report"
                )
            }
        }
    }

    private fun addTextInput(inputField: InputField, multiLine: Boolean = false) {
        val input = ProtonInput(requireContext())
        input.labelText = inputField.label
        input.hintText = inputField.placeholder
        if (multiLine) {
            input.minLines = MULTILINE_MIN_LINE
            input.setSingleLine(false)
        }
        input.tag = inputField.isMandatory

        // Add contentDescription as label for automated UI tests
        input.contentDescription = inputField.label
        binding.dynamicContent.addView(input)
        inputMap[inputField] = object : ReportBugActivityViewModel.DynamicInputUI {
            override fun getSubmitText(): String {
                return input.text.toString()
            }

            override fun setInputError(error: String) {
                input.setInputError(error)
            }
        }
    }

    private fun addDropDown(inputField: InputField) {
        val input = ProtonAutoCompleteInput(requireContext())
        input.labelText = inputField.label
        input.tag = inputField.isMandatory
        input.hintText = inputField.placeholder

        // Add contentDescription as label for automated UI tests
        input.contentDescription = inputField.label
        val serverSelection = registerForActivityResult(DropdownSelectionActivity.createContract()) {
            input.text = it?.displayName
        }
        input.setOnClickListener {
            serverSelection.launch(DropdownSelectionList(inputField.dropdownOptions.map {
                DropdownSelection(it.label, it.submitLabel)
            }))
        }

        binding.dynamicContent.addView(input)
        inputMap[inputField] = object : ReportBugActivityViewModel.DynamicInputUI {
            override fun getSubmitText(): String? {
                return inputField.dropdownOptions.firstOrNull { it.label == input.text.toString() }?.submitLabel
            }

            override fun setInputError(error: String) {
                input.setInputError(error)
            }
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
