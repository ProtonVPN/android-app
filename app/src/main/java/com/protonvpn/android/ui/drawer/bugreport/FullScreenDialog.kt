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
package com.protonvpn.android.ui.drawer.bugreport

import android.os.Bundle
import androidx.core.view.isVisible
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityFullScreenDialogBinding
import com.protonvpn.android.utils.HtmlTools
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FullScreenDialog : BaseActivityV2() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityFullScreenDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initUI(binding)
    }

    private fun initUI(binding: ActivityFullScreenDialogBinding) = with(binding) {
        backButton.setOnClickListener { finish() }

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
