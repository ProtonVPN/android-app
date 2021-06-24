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
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivityReportBinding
import com.protonvpn.android.utils.ViewUtils.hideKeyboard
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContentLayout(R.layout.activity_report)
class ReportBugActivity : BaseActivityV2<ActivityReportBinding, ReportBugActivityViewModel>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private val editReport: EditText get() = binding.layoutReport.editReport
    private val editEmail: EditText get() = binding.layoutReport.editEmail

    override fun initViewModel() {
        viewModel = ViewModelProvider(this, viewModelFactory).get(ReportBugActivityViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbarWithUpEnabled(binding.layoutAppbar.toolbar)
        addHideKeyboard(binding.coordinator)
        initUi()
    }

    private fun initUi() = with(binding) {
        viewModel.state.observe(this@ReportBugActivity, Observer { state ->
            editEmail.error =  state.emailError?.let { getString(it) }
            editReport.error = state.reportError?.let { getString(it) }
        })
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

    private fun postReport() {
        lifecycleScope.launch {
            val isSuccess = viewModel.prepareAndPostReport(
                binding.loadingContainer,
                editEmail.text.toString(),
                editReport.text.toString(),
                binding.layoutReport.switchAttachLog.switchProton.isChecked
            )
            if (isSuccess) {
                Toast.makeText(
                    this@ReportBugActivity,
                    R.string.bugReportThankYouToast,
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
}
