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

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View.OnFocusChangeListener
import androidx.leanback.widget.ImageCardView
import com.protonvpn.android.R

/**
 * This Presenter will display cards which consists of a single icon which will be highlighted by a
 * surrounding circle when the card is focused. AndroidTV uses these cards for entering settings
 * menu.
 */
class IconCardPresenter(context: Context?) : ImageCardViewPresenter(context, R.style.IconCardTheme) {

    override fun onCreateView(): ImageCardView {
        val imageCardView = super.onCreateView()
        val image = imageCardView.mainImageView
        image.background.alpha = 0
        imageCardView.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            animateIconBackground(image.background, hasFocus)
        }
        return imageCardView
    }

    private fun animateIconBackground(drawable: Drawable, hasFocus: Boolean) {
        val from = if (hasFocus) 0 else 255
        val to = if (hasFocus) 255 else 0
        ObjectAnimator.ofInt(drawable, "alpha", from, to)
                .setDuration(ANIMATION_DURATION)
                .start()
    }

    companion object {
        private const val ANIMATION_DURATION = 200L
    }
}
