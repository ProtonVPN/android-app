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

package com.protonvpn.android.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ActivityWhatsNewBinding
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.utils.onClick
import me.proton.core.presentation.utils.viewBinding

enum class WhatsNewDialogType {
    Free, Paid
}

@AndroidEntryPoint
class WhatsNewActivity : AppCompatActivity() {

    private val binding by viewBinding(ActivityWhatsNewBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val typeString = intent.getStringExtra(EXTRA_TYPE)
        val type = requireNotNull(WhatsNewDialogType.values().find { it.name == typeString })

        with(binding) {
            when(type) {
                WhatsNewDialogType.Free -> {
                    textSecondSectionTitle.setText(R.string.whats_new_added_countries_title)
                    textSecondSectionDescription.setText(R.string.whats_new_added_countries_description)
                }
                WhatsNewDialogType.Paid -> {
                    textSecondSectionTitle.setText(R.string.whats_new_recents_title)
                    textSecondSectionDescription.setText(R.string.whats_new_recents_description)
                }
            }
            gotItButton.onClick {
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_TYPE = "type"

        fun launch(context: Context, type: WhatsNewDialogType) {
            val intent = Intent(context, WhatsNewActivity::class.java).apply {
                putExtra(EXTRA_TYPE, type.name)
            }
            context.startActivity(intent)
        }
    }
}
