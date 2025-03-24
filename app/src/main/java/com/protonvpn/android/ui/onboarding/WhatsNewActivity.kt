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

package com.protonvpn.android.ui.onboarding

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.protonvpn.android.databinding.ActivityWhatsNewBinding
import com.protonvpn.android.widget.WidgetManager
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.utils.onClick
import me.proton.core.presentation.utils.viewBinding
import javax.inject.Inject

@AndroidEntryPoint
class WhatsNewActivity : AppCompatActivity() {

    @Inject lateinit var widgetManager: WidgetManager

    private val binding by viewBinding(ActivityWhatsNewBinding::inflate)

    @TargetApi(26)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        with(binding) {
            gotItButton.onClick {
                finish()
            }
            addWidgetButton.isVisible = widgetManager.supportsNativeWidgetSelector()
            addWidgetButton.onClick {
                widgetManager.openNativeWidgetSelector()
                finish()
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, WhatsNewActivity::class.java)
            context.startActivity(intent)
        }
    }
}
