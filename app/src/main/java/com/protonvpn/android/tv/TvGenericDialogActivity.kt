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
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.DialogTvGenericBinding
import com.protonvpn.android.tv.main.MainViewModel
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ViewUtils.initLolipopButtonFocus
import javax.inject.Inject

@ContentLayout(R.layout.dialog_tv_generic)
class TvGenericDialogActivity : BaseTvActivity<DialogTvGenericBinding>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    val viewModel by viewModels<MainViewModel> { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initUI()
    }

    private fun initUI() = with(binding) {
        backButton.setOnClickListener { finish() }
        backButton.initLolipopButtonFocus()

        intent.extras?.getString(EXTRA_TITLE)?.let {
            textTitle.text = it
        } ?: run { textTitle.isVisible = false }

        intent.extras?.getString(EXTRA_DESCRIPTION)?.let {
            textDescription.text = HtmlTools.fromHtml(it)
        } ?: run { textDescription.isVisible = false }

        intent.extras?.getInt(EXTRA_ICON_RES)?.let {
            image.setImageResource(it)
        } ?: run { image.isVisible = false }
    }

    companion object {
        const val EXTRA_TITLE = "Title"
        const val EXTRA_DESCRIPTION = "Description"
        const val EXTRA_ICON_RES = "IconRes"
    }
}
