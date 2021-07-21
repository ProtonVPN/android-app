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

package com.protonvpn.android.ui.drawer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.ViewModel
import com.google.android.material.internal.TextWatcherAdapter
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivitySettingsMtuBinding
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.ActivityResultUtils
import me.proton.core.presentation.ui.view.ProtonInput
import org.strongswan.android.utils.Constants.MTU_MAX
import org.strongswan.android.utils.Constants.MTU_MIN
import javax.inject.Inject

@ContentLayout(R.layout.activity_settings_mtu)
class SettingsMtuActivity : BaseActivityV2<ActivitySettingsMtuBinding, ViewModel>() {

    @Inject lateinit var userData: UserData

    override fun initViewModel() {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbarWithUpEnabled(binding.contentAppbar.toolbar)

        with(binding) {
            inputMtu.text = userData.mtuSize.toString()
            inputMtu.addTextChangedListener(object : TextWatcherAdapter() {
                override fun afterTextChanged(s: Editable) {
                    buttonSave.isEnabled = asValidMtu(s.toString()) != null
                }
            })
            inputMtu.setOnEditorActionListener(TextView.OnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    validateAndSave(inputMtu)
                    true
                } else {
                    false
                }
            })

            buttonSave.setOnClickListener { validateAndSave(inputMtu) }
        }
    }

    private fun asValidMtu(text: String): Int? =
        text.toIntOrNull()?.takeIf { number -> number in MTU_MIN..MTU_MAX }

    private fun validateAndSave(inputMtu: ProtonInput) {
        val newMtu = asValidMtu(inputMtu.text.toString())
        if (newMtu != null) {
            userData.mtuSize = newMtu
            finish()
        } else {
            inputMtu.setInputError(getString(R.string.settingsMtuRangeInvalid))
        }
    }

    companion object {
        fun createContract() = object : ActivityResultContract<Int, Int>() {
            override fun createIntent(context: Context, input: Int): Intent =
                Intent(context, SettingsMtuActivity::class.java).apply {
                    putExtra(ActivityResultUtils.INPUT_KEY, input)
                }

            override fun parseResult(resultCode: Int, intent: Intent?): Int? =
                if (resultCode == Activity.RESULT_OK) {
                    intent?.getIntExtra(ActivityResultUtils.OUTPUT_KEY, 0)
                } else {
                    null
                }
        }
    }
}
