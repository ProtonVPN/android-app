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

package com.protonvpn.android.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import com.protonvpn.android.databinding.ActivitySecureCoreSpeedInfoDialogBinding
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.ViewUtils.viewBinding
import com.protonvpn.android.utils.openProtonUrl

class SecureCoreSpeedInfoActivity : AppCompatActivity() {

    private val binding by viewBinding(ActivitySecureCoreSpeedInfoDialogBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        with(binding) {
            textLearnMore.setOnClickListener { openProtonUrl(Constants.SECURE_CORE_INFO_URL) }

            buttonActivate.setOnClickListener {
                dontShowAgain(checkboxDontShowAgain.isChecked)
                onActivateSecureCore()
            }
            buttonCancel.setOnClickListener {
                dontShowAgain(checkboxDontShowAgain.isChecked)
                finish()
            }
        }
    }

    private fun onActivateSecureCore() {
        val result = Intent().apply {
            putExtra(RESULT_EXTRA_KEY, true)
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun dontShowAgain(dontShowAgain: Boolean) {
        if (dontShowAgain) {
            Storage.saveBoolean(PREF_SHOW_SECURE_CORE_SWITCH_INFO_DIALOG, false)
        }
    }

    companion object {
        private const val PREF_SHOW_SECURE_CORE_SWITCH_INFO_DIALOG = "PREF_SHOW_SECURE_CORE_SWITCH_INFO_DIALOG"
        private const val RESULT_EXTRA_KEY = "activate"

        @JvmStatic
        fun createContract() = object : ActivityResultContract<Unit, Boolean>() {
            override fun createIntent(context: Context, input: Unit) =
                Intent(context, SecureCoreSpeedInfoActivity::class.java)

            override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
                intent?.getBooleanExtra(RESULT_EXTRA_KEY, false) ?: false

            override fun getSynchronousResult(context: Context, input: Unit): SynchronousResult<Boolean>? =
                if (Storage.getBoolean(PREF_SHOW_SECURE_CORE_SWITCH_INFO_DIALOG, true)) {
                    null
                } else {
                    SynchronousResult(true) // Don't show the dialog.
                }
        }
    }
}
