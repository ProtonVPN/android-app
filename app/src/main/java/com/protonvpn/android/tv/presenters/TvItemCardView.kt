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
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.leanback.widget.BaseCardView
import com.protonvpn.android.R
import com.protonvpn.android.databinding.TvItemGridBinding
import com.protonvpn.android.tv.detailed.CountryDetailFragment
import com.protonvpn.android.tv.models.Card
import com.protonvpn.android.tv.models.CountryCard
import com.protonvpn.android.utils.setColorTintRes

class TvItemCardView(context: Context?) : BaseCardView(context, null, R.style.DefaultCardTheme) {

    val binding: TvItemGridBinding = TvItemGridBinding.inflate(LayoutInflater.from(getContext()), this, true)

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
        card.title?.let { title ->
            textTitle.text = title.text
            title.resId?.let { imageTitle.setImageResource(it) }
        }
        titleLayout.setBackgroundResource(card.title?.backgroundColorRes ?: 0)
        imageTitle.isVisible = card.title?.resId != null

        with(card.backgroundImage) {
            imageBackground.setColorTintRes(tintRes)
            imageBackground.setImageResource(resId)
        }
        card.bottomTitle?.let { title ->
            textDescription.text = title.text
            title.resId?.let { imageBottomTitle.setImageResource(it) }
        }
        bottomTitle.setBackgroundResource(card.bottomTitle?.backgroundColorRes ?: 0)
        imageBottomTitle.isVisible = card.bottomTitle?.resId != null

        if (card is CountryCard) {
            imageBackground.transitionName =
                CountryDetailFragment.transitionNameForCountry(card.vpnCountry.flag)
        }
    }
}
