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
import android.os.SystemClock
import android.text.Editable
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ActivitySettingsMtuBinding
import com.protonvpn.android.ui.SaveableSettingsActivity
import com.protonvpn.android.utils.DefaultTextWatcher
import com.protonvpn.android.utils.launchAndCollectIn
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.lang.RuntimeException

class TestCrash : RuntimeException("Test exception, everything's fine")

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
            textDescription.setOnClickListener(CountingClickListener { throw TestCrash() })

            viewModel.eventInvalidMtu.launchAndCollectIn(this@SettingsMtuActivity) {
                inputMtu.setInputError(getString(R.string.settingsMtuRangeInvalid))
            }
        }
    }

    private class CountingClickListener(private val onTriggered: () -> Unit) : View.OnClickListener {
        private var consecutiveClicks = 0
        private var lastClickTimestampMs = 0L

        override fun onClick(view: View) {
            val now = SystemClock.elapsedRealtime()
            val timeSincePreviousClick = now - lastClickTimestampMs
            lastClickTimestampMs = now
            if (timeSincePreviousClick < CONSECUTIVE_CLICK_TIME_DIFF_MS) {
                ++consecutiveClicks
                if (consecutiveClicks == TRIGGER_CLICK_COUNT) {
                    consecutiveClicks = 0
                    onTriggered()
                }
            } else {
                consecutiveClicks = 0
            }
        }

        companion object {
            private const val CONSECUTIVE_CLICK_TIME_DIFF_MS = 500
            private const val TRIGGER_CLICK_COUNT = 7
        }
    }

    companion object {
        fun createContract() = createContract(SettingsMtuActivity::class)
    }
}
