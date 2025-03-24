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

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.view.WindowCompat
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.ui.planupgrade.UpgradeOnboardingDialogActivity
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.openUrl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OnboardingActivity : BaseActivityV2() {

    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val scope = rememberCoroutineScope()
            VpnTheme {
                OnboardingScreen(
                    bannerAction = {
                        openUrl(Constants.NO_LOGS_AUDIT_URL)
                    },
                    action = {
                        scope.launch {
                            if (viewModel.isInAppUpgradeAllowed())
                                UpgradeOnboardingDialogActivity.launch(this@OnboardingActivity)
                            finish()
                        }
                    }
                )
            }
        }
    }
}
