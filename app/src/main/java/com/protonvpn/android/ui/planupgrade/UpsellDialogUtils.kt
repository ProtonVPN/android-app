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

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ActivityUpsellDialogBinding
import com.protonvpn.android.databinding.ItemUpgradeFeatureBinding
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ViewUtils.toPx
import kotlinx.coroutines.launch
import me.proton.core.presentation.utils.onClick

fun ViewGroup.addFeature(
    @StringRes textRes: Int,
    @DrawableRes iconRes: Int,
    highlighted: Boolean = false
) {
    val htmlText = context.getString(textRes)
    addFeature(HtmlTools.fromHtml(htmlText), iconRes, highlighted)
}

fun ViewGroup.addFeature(text: CharSequence, @DrawableRes iconRes: Int, highlighted: Boolean = false) {
    val views = ItemUpgradeFeatureBinding.inflate(LayoutInflater.from(context), this, true)
    views.text.text = text
    views.icon.setImageResource(iconRes)
    if (highlighted) {
        val highlightColor = resources.getColor(R.color.protonVpnGreen, null)
        views.text.setTextColor(highlightColor)
        views.icon.setColorFilter(highlightColor)
    }
}

fun ActivityUpsellDialogBinding.initBinding(
    activity: AppCompatActivity,
    imageResource: Int?,
    title: String,
    message: CharSequence? = null,
    @StringRes mainButtonLabel: Int,
    mainButtonAction: () -> Unit,
    @StringRes otherButtonLabel: Int? = null,
    otherButtonAction: () -> Unit = { activity.finish() },
    initFeatures: (LinearLayout.() -> Unit)? = null
) {
    with(activity) {
        // Set drawing under system bars and update paddings accordingly
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = 24.toPx() + insets.top,
                bottom = insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    imagePicture.isVisible = imageResource != null
    imageResource?.let { imagePicture.setImageResource(it) }

    textTitle.text = title
    textMessage.isVisible = message != null
    message?.let { textMessage.text = it }

    initFeatures?.invoke(layoutFeatureItems)
    if (layoutFeatureItems.isNotEmpty())
        layoutFeatureItemsContainer.isVisible = true

    buttonMainAction.setText(mainButtonLabel)
    buttonMainAction.onClick(mainButtonAction)
    if (otherButtonLabel != null) {
        buttonOther.isVisible = true
        buttonOther.setText(otherButtonLabel)
        buttonOther.onClick(otherButtonAction)
    }
}

fun ActivityUpsellDialogBinding.initUpgradeBinding(
    activity: UpgradeDialogActivity,
    viewModel: UpgradeDialogViewModel,
    imageResource: Int? = null,
    title: String,
    message: CharSequence? = null,
    @StringRes otherButtonLabel: Int? = if (viewModel.showUpgrade()) R.string.upgrade_not_now_button else null,
    otherButtonAction: () -> Unit = { activity.finish() },
    initFeatures: (LinearLayout.() -> Unit)? = null
) {
    val showUpgrade = viewModel.showUpgrade()
    val mainButtonAction : () -> Unit = {
        if (showUpgrade) {
            activity.lifecycleScope.launch {
                viewModel.planUpgrade()
            }
        } else {
            activity.finish()
        }
    }
    initBinding(
        activity,
        imageResource = imageResource,
        title = title,
        message = message,
        mainButtonLabel = if (showUpgrade) R.string.upgrade else R.string.close,
        mainButtonAction = mainButtonAction,
        otherButtonLabel = otherButtonLabel,
        otherButtonAction = otherButtonAction,
        initFeatures = initFeatures
    )
}
