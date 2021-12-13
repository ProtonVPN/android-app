/*
 * Copyright (c) 2018 Proton Technologies AG
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

import android.view.View
import android.widget.ImageView
import androidx.core.animation.doOnEnd
import com.protonvpn.android.components.ProtonActionMenu
import com.protonvpn.android.utils.Storage

object OnboardingDialogs {

    @JvmStatic
    fun showDialogOnView(
        tooltipManager: TooltipManager,
        highlightView: View,
        anchorView: View,
        title: String,
        description: String,
        booleanName: String
    ) {
        if (!Storage.getBoolean(booleanName)) {
            tooltipManager.show(highlightView, anchorView, title, description) {
                Storage.saveBoolean(booleanName, true)
            }
        }
    }

    @JvmStatic
    fun showDialogOnFab(
        tooltipManager: TooltipManager,
        fabMenu: ProtonActionMenu,
        title: String,
        description: String,
        booleanName: String
    ) {
        if (!Storage.getBoolean(booleanName)) {
            val context = fabMenu.context
            val replacements = arrayOf(
                View(context).apply {
                    background = fabMenu.actionButton.background

                },
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER
                    setImageDrawable(fabMenu.menuIconView.drawable)
                }
            )
            tooltipManager.showWithReplacement(
                fabMenu.actionButton,
                replacements,
                title,
                description,
                onShown = {
                    fabMenu.visibility = View.INVISIBLE
                    animateFab(replacements[0])
                },
                onDismissedAction = {
                    fabMenu.visibility = View.VISIBLE
                    Storage.saveBoolean(booleanName, true)
                }
            )
        }
    }

    private fun animateFab(view: View) {
        val animSetXY = ProtonActionMenu.createPulseAnimator(view)
        animSetXY.doOnEnd {
            if (view.isAttachedToWindow)
                animateFab(view)
        }
        animSetXY.start()
    }
}
