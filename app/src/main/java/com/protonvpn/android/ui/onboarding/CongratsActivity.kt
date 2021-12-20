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

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityOnboardingCongratsBinding
import com.protonvpn.android.ui.planupgrade.UpgradePlusOnboardingDialogActivity
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.utils.onClick
import me.proton.core.presentation.utils.viewBinding

@AndroidEntryPoint
class CongratsActivity : BaseActivityV2() {

    private val viewModel by viewModels<CongratsViewModel>()
    private val binding by viewBinding(ActivityOnboardingCongratsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        viewModel.server?.let { binding.connectedTo.setCountry(it) }
        binding.continueButton.onClick {
            startActivity(Intent(this, UpgradePlusOnboardingDialogActivity::class.java))
            finish()
        }
    }
}
