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
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ActivitySettingsMtuBinding
import com.protonvpn.android.ui.SaveableSettingsActivity
import com.protonvpn.android.utils.DefaultTextWatcher
import com.protonvpn.android.utils.launchAndCollectIn
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsMtuActivity : SaveableSettingsActivity<SettingsMtuViewModel>() {

    override val viewModel: SettingsMtuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsMtuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initToolbarWithUpEnabled(binding.contentAppbar.toolbar)

        with(binding) {
            lifecycleScope.launch {
                inputMtu.text = viewModel.getMtu()
                inputMtu.addTextChangedListener(object : DefaultTextWatcher() {
                    override fun afterTextChanged(s: Editable) {
                        viewModel.onMtuTextChanged(s.toString())
                    }
                })
                inputMtu.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        viewModel.saveAndClose()
                        true
                    } else {
                        false
                    }
                }
            }

            viewModel.eventInvalidMtu.launchAndCollectIn(this@SettingsMtuActivity) {
                inputMtu.setInputError(getString(R.string.settingsMtuRangeInvalid))
            }
        }
    }

    companion object {
        fun createContract() = createContract(SettingsMtuActivity::class)
    }
}
