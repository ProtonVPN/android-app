/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.ui.planupgrade

import android.widget.LinearLayout
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.protonvpn.android.R
import com.protonvpn.android.databinding.FragmentPlanHighlightsBinding
import me.proton.core.presentation.utils.viewBinding

abstract class PlanHighlightsFragment : Fragment(R.layout.fragment_plan_highlights) {

    protected val binding by viewBinding(FragmentPlanHighlightsBinding::bind)

    protected fun FragmentPlanHighlightsBinding.set(
        imageResource: Int?,
        title: String,
        message: CharSequence? = null,
        initFeatures: (LinearLayout.() -> Unit)? = null
    ) {
        imagePicture.isVisible = imageResource != null
        imageResource?.let { imagePicture.setImageResource(it) }

        textTitle.text = title
        textMessage.isVisible = message != null
        message?.let { textMessage.text = it }

        initFeatures?.invoke(layoutFeatureItems)
        if (layoutFeatureItems.isNotEmpty()) {
            layoutFeatureItems.isVisible = true
        }
    }
}
