/*
 * Copyright (c) 2017 Proton Technologies AG
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
package com.protonvpn.android.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.res.Resources
import android.view.animation.OvershootInterpolator
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.github.clans.fab.FloatingActionMenu
import com.protonvpn.android.R

object AnimationTools {

    @JvmStatic
    fun setScaleAnimationToMenuIcon(menu: FloatingActionMenu, isConnected: () -> Boolean) {
        val set = AnimatorSet()
        val scaleOutX = ObjectAnimator.ofFloat(menu.menuIconView, "scaleX", 1.0f, 0.2f)
        val scaleOutY = ObjectAnimator.ofFloat(menu.menuIconView, "scaleY", 1.0f, 0.2f)
        val scaleInX = ObjectAnimator.ofFloat(menu.menuIconView, "scaleX", 0.2f, 1.0f)
        val scaleInY = ObjectAnimator.ofFloat(menu.menuIconView, "scaleY", 0.2f, 1.0f)

        scaleOutX.duration = 100
        scaleOutY.duration = 100
        scaleInX.duration = 200
        scaleInY.duration = 200

        scaleInX.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                val icon = if (menu.isOpened) {
                    val logo = if (isConnected()) R.drawable.ic_vpn_icon_colorful else R.drawable.ic_vpn_icon_grayscale
                    AppCompatResources.getDrawable(menu.context, logo)
                } else {
                    AppCompatResources.getDrawable(menu.context, R.drawable.ic_proton_cross)?.apply {
                        setTint(ContextCompat.getColor(menu.context, R.color.shade_0))
                    }
                }
                menu.menuIconView.setImageDrawable(icon)
            }
        })
        set.play(scaleOutX).with(scaleOutY)
        set.play(scaleInX).with(scaleInY).after(scaleOutX)
        set.interpolator = OvershootInterpolator(2f)
        menu.iconToggleAnimatorSet = set
    }

    fun convertDpToPixel(dp: Int): Int = (dp * Resources.getSystem().displayMetrics.density).toInt()

    fun convertPixelsToDp(px: Int): Int = (px / Resources.getSystem().displayMetrics.density).toInt()
}
