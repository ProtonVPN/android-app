/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.android.ui.drawer

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.protonvpn.android.R
import com.protonvpn.android.databinding.DrawerNotificationViewBinding
import com.protonvpn.android.ui.home.profiles.HomeViewModel
import com.protonvpn.android.utils.getThemeColor
import com.protonvpn.android.utils.openProtonUrl

class DrawerNotificationsContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    fun updateNotifications(
        activity: AppCompatActivity,
        apiNotifications: List<HomeViewModel.OfferViewModel>
    ) {
        removeAllViews()
        isVisible = if (apiNotifications.isNotEmpty()) {
            apiNotifications.forEach { offer ->
                addView(DrawerNotificationView.create(activity, offer))
            }
            true
        } else {
            false
        }
    }
}

class DrawerNotificationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private fun updateIcon(item: HomeViewModel.OfferViewModel) {
        Glide.with(context)
            .asDrawable()
            .load(item.iconUrl)
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    val size = resources.getDimensionPixelSize(R.dimen.drawer_icon_size)
                    resource.setBounds(0, 0, size, size)
                    resource.setTint(
                        getThemeColor(if (!item.visited) R.attr.colorAccent else R.attr.proton_icon_norm)
                    )
                    setCompoundDrawablesRelative(resource, null, null, null)
                }
                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    companion object {

        fun create(
            activity: AppCompatActivity,
            item: HomeViewModel.OfferViewModel
        ) = DrawerNotificationViewBinding.inflate(activity.layoutInflater).drawerNotificationItem.apply {
            val accent = getThemeColor(R.attr.colorAccent)
            val placeholder = ResourcesCompat.getDrawable(resources, R.drawable.ic_drawer_notification, null)
            placeholder?.setTint(if (!item.visited) accent else getThemeColor(R.attr.proton_icon_norm))
            setCompoundDrawablesRelativeWithIntrinsicBounds(placeholder, null, null, null)
            updateIcon(item)
            text = item.label
            if (!item.visited)
                setTextColor(accent)
            setOnClickListener {
                activity.openProtonUrl(item.url)
                item.setVisited()
            }
        }
    }
}
