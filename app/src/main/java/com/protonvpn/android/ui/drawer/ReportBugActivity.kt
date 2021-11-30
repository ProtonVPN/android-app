/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.android.ui.drawer

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.NetworkFrameLayout
import com.protonvpn.android.databinding.ActivityReportBinding
import com.protonvpn.android.utils.ViewUtils.hideKeyboard
import com.protonvpn.android.utils.ViewUtils.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.proton.core.presentation.ui.view.ProtonInput

@AndroidEntryPoint
class ReportBugActivity : BaseActivityV2() {

    private val binding by viewBinding(ActivityReportBinding::inflate)
    private val viewModel: ReportBugActivityViewModel by viewModels()
    private val editReport: ProtonInput get() = binding.layoutReport.editReport
    private val editEmail: ProtonInput get() = binding.layoutReport.editEmail

    private var submitJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initToolbarWithUpEnabled(binding.layoutAppbar.toolbar)
        addHideKeyboard(binding.coordinator)
        initUi()
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            true
        } else {
            super.onOptionsItemSelected(item)
        }

    override fun onBackPressed() {
        with(binding) {
            when(loadingContainer.state) {
                NetworkFrameLayout.State.ERROR ->
                    loadingContainer.switchToEmpty()
                NetworkFrameLayout.State.LOADING -> {
                    submitJob?.cancel()
                    loadingContainer.switchToEmpty()
                }
                else -> super.onBackPressed()
            }
        }
    }

    private fun initUi() = with(binding) {
        viewModel.state.observe(this@ReportBugActivity, Observer { updateState(it) })
        loadingContainer.setRetryListener {
            postReport()
        }
        layoutReport.buttonReport.setOnClickListener {
            postReport()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun addHideKeyboard(view: View) {
        if (view !is EditText) {
            view.setOnTouchListener { _, _ ->
                this.hideKeyboard(binding.layoutReport.editReport)
                false
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val innerView = view.getChildAt(i)
                addHideKeyboard(innerView)
            }
        }
    }

    private fun updateState(state: ReportBugActivityViewModel.ViewState) = with(binding) {
        when (state) {
            is ReportBugActivityViewModel.ViewState.Form -> {
                loadingContainer.switchToEmpty()
                editEmail.setOrClearError(state.emailError?.let { getString(it) })
                editReport.setOrClearError(state.reportError?.let { getString(it) })
            }
            is ReportBugActivityViewModel.ViewState.Submitting ->
                loadingContainer.switchToLoading()
            is ReportBugActivityViewModel.ViewState.Error ->
                loadingContainer.switchToRetry(state.error)
            is ReportBugActivityViewModel.ViewState.Finish -> {
                delegatedSnackManager.postSnack(getString(R.string.bugReportThankYouToast), true)
                finish()
            }
        }
    }

    private fun postReport() {
        submitJob?.cancel()
        submitJob = lifecycleScope.launch {
            viewModel.prepareAndPostReport(
                editEmail.text.toString(),
                editReport.text.toString(),
                binding.layoutReport.checkboxIncludeLogs.isChecked
            )
        }
    }

    private fun ProtonInput.setOrClearError(error: String?) {
        if (error == null) clearInputError()
        else setInputError(error)
    }
}
