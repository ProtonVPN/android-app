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

package com.protonvpn.android.ui.settings

import android.os.Bundle
import android.text.Editable
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.internal.TextWatcherAdapter
import com.protonvpn.android.R
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivitySettingsMtuBinding
import com.protonvpn.android.ui.SaveableSettingsActivity
import com.protonvpn.android.utils.launchAndCollectIn
import javax.inject.Inject

@ContentLayout(R.layout.activity_settings_mtu)
class SettingsMtuActivity : SaveableSettingsActivity<ActivitySettingsMtuBinding, SettingsMtuViewModel>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun initViewModel() {
        viewModel =
            ViewModelProvider(this, viewModelFactory).get(SettingsMtuViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbarWithUpEnabled(binding.contentAppbar.toolbar)

        with(binding) {
            inputMtu.text = viewModel.mtu
            inputMtu.addTextChangedListener(object : TextWatcherAdapter() {
                override fun afterTextChanged(s: Editable) {
                    viewModel.onMtuTextChanged(s.toString())
                }
            })
            inputMtu.setOnEditorActionListener(TextView.OnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    viewModel.saveAndClose()
                    true
                } else {
                    false
                }
            })

            viewModel.eventInvalidMtu.launchAndCollectIn(this@SettingsMtuActivity) {
                inputMtu.setInputError(getString(R.string.settingsMtuRangeInvalid))
            }
        }
    }

    companion object {
        fun createContract() = createContract(SettingsMtuActivity::class)
    }
}
