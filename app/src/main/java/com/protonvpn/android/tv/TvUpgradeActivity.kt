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
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.databinding.DialogTvUpgradeBinding
import com.protonvpn.android.tv.main.TvMainViewModel
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ViewUtils.initLolipopButtonFocus
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TvUpgradeActivity : BaseTvActivity() {

    val viewModel by viewModels<TvMainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DialogTvUpgradeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initUI(binding)
    }

    private fun initUI(binding: DialogTvUpgradeBinding) = with(binding) {
        backButton.initLolipopButtonFocus()
        backButton.setOnClickListener { finish() }
        val htmlDescription = getString(R.string.tv_upgrade_url_details, Constants.TV_UPGRADE_LINK)
        textUpgradeDetails.text = HtmlTools.fromHtml(htmlDescription)
        textUpgradeDetails.isVisible = viewModel.purchaseEnabled()

        with(streamIcons) {
            streamingNetflix.addStreamingView("Netflix", getIcon(R.drawable.ic_streaming_netflix))
            streamingDisney.addStreamingView("Disney+", getIcon(R.drawable.ic_streaming_disney))
            streamingPrime.addStreamingView("Prime", getIcon(R.drawable.ic_streaming_prime))
        }
    }

    private fun getIcon(@DrawableRes iconRes: Int) =
        if (viewModel.displayStreamingIcons) iconRes else null
}
