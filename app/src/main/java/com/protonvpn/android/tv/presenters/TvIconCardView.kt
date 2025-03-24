/*
 * Copyright (c) 2020 Proton AG
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
package com.protonvpn.android.tv.presenters

import android.content.Context
import android.view.LayoutInflater
import androidx.leanback.widget.BaseCardView
import com.protonvpn.android.R
import com.protonvpn.android.databinding.TvIconCardBinding
import com.protonvpn.android.tv.models.Card

class TvIconCardView(context: Context) : BaseCardView(context, null, R.style.DefaultCardTheme) {

    val binding: TvIconCardBinding = TvIconCardBinding.inflate(LayoutInflater.from(getContext()), this, true)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        alpha = if (selected) 1f else 0.5f
    }

    fun updateUi(card: Card) = with(binding) {
        alpha = if (isSelected) 1f else 0.5f
        card.backgroundImage?.resId?.let(icon::setImageResource)
        card.title?.text?.let { title.text = it }
    }
}
