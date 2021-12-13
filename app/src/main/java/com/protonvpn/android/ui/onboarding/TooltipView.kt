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

package com.protonvpn.android.ui.onboarding

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.view.marginLeft
import androidx.core.view.updateLayoutParams
import com.protonvpn.android.databinding.TooltipBinding

class TooltipView : LinearLayout {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    val binding = TooltipBinding.inflate(LayoutInflater.from(context), this)

    val textTitle = binding.textTitle
    val textMessage = binding.textMessage

    init {
        orientation = VERTICAL
    }

    fun setArrowPosition(centerHorizontal: Int, down: Boolean) {
        val arrow = if (down) binding.imageArrowDown else binding.imageArrowUp
        binding.imageArrowDown.isVisible = down
        binding.imageArrowUp.isVisible = !down

        arrow.updateLayoutParams<LayoutParams> {
            leftMargin = centerHorizontal - arrow.drawable.intrinsicWidth / 2
        }
    }

    fun setOnAcknowledgedListener(listener: OnClickListener) {
        binding.buttonGotIt.setOnClickListener(listener)
    }
}
