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

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View.GONE
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.Observer
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.NetworkFrameLayout
import com.protonvpn.android.databinding.ActivityDynamicReportBinding
import com.protonvpn.android.databinding.DialogCreateAccountButtonsBinding
import com.protonvpn.android.utils.ViewUtils.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class DynamicReportActivity : BaseActivityV2() {

    private val binding by viewBinding(ActivityDynamicReportBinding::inflate)
    private val viewModel: ReportBugActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        if (isTv()) {
            binding.layoutAppbar.toolbar.visibility = GONE
        } else {
            initToolbarWithUpEnabled(binding.layoutAppbar.toolbar)
        }
        binding.loadingContainer.setRetryListener { binding.loadingContainer.switchToEmpty() }
        viewModel.state.observe(this, Observer {
            handleStateChanges(it)
        })
        viewModel.event
            .flowWithLifecycle(lifecycle)
            .onEach(::handleUiEvent)
            .launchIn(lifecycleScope)
    }

    override fun onBackPressed() {
        if (binding.loadingContainer.state == NetworkFrameLayout.State.ERROR) {
            binding.loadingContainer.switchToEmpty()
        } else {
            super.onBackPressed()
        }
    }

    private fun handleStateChanges(state: ReportBugActivityViewModel.ViewState) {
        when (state) {
            is ReportBugActivityViewModel.ViewState.Loading ->
                Unit
            is ReportBugActivityViewModel.ViewState.Categories ->
                addFragment(CategoryFragment.newInstance(), false)
            is ReportBugActivityViewModel.ViewState.Suggestions ->
                addFragment(SuggestionsFragment.newInstance(state.category))
            is ReportBugActivityViewModel.ViewState.Report ->
                addFragment(ReportFragment.newInstance(state.category))
            is ReportBugActivityViewModel.ViewState.Finish ->
                finishAndShowSuccessDialog()
            is ReportBugActivityViewModel.ViewState.Error ->
                binding.loadingContainer.switchToRetry(state.error)
            is ReportBugActivityViewModel.ViewState.SubmittingReport ->
                Unit
        }
    }

    private fun handleUiEvent(event: ReportBugActivityViewModel.UiEvent) {
        when (event) {
            ReportBugActivityViewModel.UiEvent.ShowLoginDialog -> {
                val buttonsView = DialogCreateAccountButtonsBinding.inflate(layoutInflater)
                val dialog = MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.dialog_create_account_title)
                    .setView(buttonsView.root)
                    .show()
                buttonsView.buttonCreateAccount.setOnClickListener {
                    viewModel.startCreateAccountFlow()
                    dialog.dismiss()
                }
                buttonsView.buttonSignIn.setOnClickListener {
                    viewModel.startSignInFlow()
                    dialog.dismiss()
                }
                buttonsView.buttonCancel.setOnClickListener { dialog.dismiss() }
            }
        }
    }

    private fun finishAndShowSuccessDialog() {
        val intent = Intent(baseContext, FullScreenDialog::class.java)
        intent.putExtra(FullScreenDialog.EXTRA_TITLE, getString(R.string.dynamic_report_success_title))
        intent.putExtra(FullScreenDialog.EXTRA_DESCRIPTION, getString(R.string.dynamic_report_success_description))
        intent.putExtra(FullScreenDialog.EXTRA_ICON_RES, R.drawable.success_report_an_issue)
        startActivity(intent)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            true
        } else {
            super.onOptionsItemSelected(item)
        }

    private fun addFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        supportFragmentManager.commit {
            if (addToBackStack) {
                addToBackStack(null)
            }
            replace(R.id.fragmentContainer, fragment)
        }
    }
}
