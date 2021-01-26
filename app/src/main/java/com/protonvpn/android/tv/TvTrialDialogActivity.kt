/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.tv

import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.DialogTvTrialBinding
import com.protonvpn.android.tv.main.MainViewModel
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ViewUtils.initLolipopButtonFocus
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

@ContentLayout(R.layout.dialog_tv_trial)
class TvTrialDialogActivity : BaseTvActivity<DialogTvTrialBinding>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    val viewModel by viewModels<MainViewModel> { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initUI()
    }

    private fun initUI() = with(binding) {
        buttonGotIt.setOnClickListener { finish() }
        buttonGotIt.initLolipopButtonFocus()
        textDetails.text = HtmlTools.fromHtml(getString(R.string.tv_trial_summary))
        lifecycleScope.launchWhenResumed {
            viewModel.getTrialPeriodFlow(baseContext).collect { textCountdown.text = it }
        }
    }
}
