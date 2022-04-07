/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.ui.account

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.view.View
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import com.google.android.material.snackbar.Snackbar
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityUseCouponBinding
import com.protonvpn.android.utils.AndroidUtils.setContentViewBinding
import com.protonvpn.android.utils.DefaultTextWatcher
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.ui.view.ProtonInput
import me.proton.core.presentation.utils.SnackType
import me.proton.core.presentation.utils.snack

@AndroidEntryPoint
class UseCouponActivity : BaseActivityV2() {

    private val viewModel by viewModels<UseCouponViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = setContentViewBinding(ActivityUseCouponBinding::inflate)
        initToolbarWithUpEnabled(binding.contentAppbar.toolbar)

        with(binding) {
            buttonApply.setOnClickListener {
                viewModel.applyCoupon(inputCode.text.toString())
            }
            inputCode.addTextChangedListener(object : DefaultTextWatcher() {
                override fun afterTextChanged(s: Editable) {
                    buttonApply.isEnabled = s.isNotBlank()
                }
            })
            inputCode.endIconMode = ProtonInput.EndIconMode.CLEAR_TEXT
            inputCode.endIconDrawable = ContextCompat.getDrawable(this@UseCouponActivity, R.drawable.ic_cross_small)
            inputCode.filters = inputCode.filters + InputFilter.AllCaps()
        }
        viewModel.viewState.asLiveData().observe(
            this,
            Observer { updateState(binding, it) }
        )
    }

    private fun updateState(binding: ActivityUseCouponBinding, state: UseCouponViewModel.ViewState) = with(binding) {
        when (state) {
            is UseCouponViewModel.ViewState.Init -> setLoadingState(false)
            is UseCouponViewModel.ViewState.Loading -> setLoadingState(true)
            is UseCouponViewModel.ViewState.SubscriptionUpgraded -> {
                delegatedSnackManager.postSnack(getString(R.string.use_coupon_success), SnackType.Success)
                finish()
            }
            is UseCouponViewModel.ViewState.SuccessButSubscriptionNotUpgradedYet -> {
                delegatedSnackManager.postSnack(
                    getString(R.string.use_coupon_success_no_upgrade_yet), SnackType.Success
                )
                finish()
            }
            is UseCouponViewModel.ViewState.CouponError -> {
                setLoadingState(false)
                inputCode.setInputError()
                errorSnackWithOk(root, state.message)
                viewModel.ackErrorState()
            }
            is UseCouponViewModel.ViewState.Error -> {
                setLoadingState(false)
                errorSnackWithOk(root, getString(state.messageRes))
                viewModel.ackErrorState()
            }
        }
    }

    private fun errorSnackWithOk(view: View, message: String) {
        view.snack(
            message,
            SnackType.Error,
            action = getString(R.string.ok),
            actionOnClick = { },
            length = Snackbar.LENGTH_INDEFINITE
        )
    }

    private fun ActivityUseCouponBinding.setLoadingState(isLoading: Boolean) {
        if (isLoading) buttonApply.setLoading() else buttonApply.setIdle()
        buttonApply.setText(if (isLoading) R.string.dialog_applying else R.string.dialog_apply)
        inputCode.isEnabled = !isLoading
    }
}
