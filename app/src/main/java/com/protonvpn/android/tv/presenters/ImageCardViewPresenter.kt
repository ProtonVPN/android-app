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
package com.protonvpn.android.tv.presenters

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.ImageView
import androidx.leanback.widget.ImageCardView
import com.protonvpn.android.R
import com.protonvpn.android.tv.models.Card

open class ImageCardViewPresenter @JvmOverloads constructor(
    context: Context?,
    cardThemeResId: Int = R.style.DefaultCardTheme
) : AbstractCardPresenter<ImageCardView>(ContextThemeWrapper(context, cardThemeResId)) {

    override fun onCreateView() = object : ImageCardView(context) {

        init {
            alpha = 0.5f
            infoVisibility = GONE
        }

        override fun setSelected(selected: Boolean) {
            super.setSelected(selected)
            alpha = if (selected) 1f else 0.5f
        }
    }

    override fun onBindViewHolder(card: Card, cardView: ImageCardView) {
        with(cardView) {
            tag = card
            titleText = card.title!!.text
            mainImageView.scaleType = ImageView.ScaleType.FIT_CENTER
            mainImageView.setImageResource(card.backgroundImage!!.resId)
            mainImageView.adjustViewBounds = true
        }
    }
}
