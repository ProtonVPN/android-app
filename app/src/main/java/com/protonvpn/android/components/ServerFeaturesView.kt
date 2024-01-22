/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ServerFeaturesViewBinding
import com.protonvpn.android.models.vpn.Partner
import com.protonvpn.android.utils.ViewUtils.toPx
import me.proton.core.presentation.R as CoreR

private object PartnerIconTag

class ServerFeaturesView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private val binding = ServerFeaturesViewBinding.inflate(LayoutInflater.from(context), this)

    @ColorInt
    var color: Int = MaterialColors.getColor(this, CoreR.attr.proton_icon_norm)
        set(value) {
            field = value
            update()
        }

    var featureIcons: Collection<FeatureIcon> = emptyList()
        set(value) {
            field = value
            update()
        }

    var partners: List<Partner> = emptyList()
        set(value) {
            field = value
            updatePartnerIconsIfNeeded()
        }
    private var currentPartners: List<Partner> = emptyList()

    var partnerIconClickListener: OnClickListener? = null
        set(value) {
            field = value
            updatePartnerClickListener()
        }

    init {
        orientation = HORIZONTAL
        setVerticalGravity(Gravity.CENTER_VERTICAL)
        if (isInEditMode) featureIcons = listOf(FeatureIcon.P2P, FeatureIcon.TOR)
        update()
    }

    private fun update() = with(binding) {
        featureIcons().forEach { it.isVisible = false }
        featureIcons.forEach {
            val iconView = when (it) {
                FeatureIcon.P2P -> iconP2P
                FeatureIcon.TOR -> iconTor
                FeatureIcon.STREAMING -> iconStreaming
                FeatureIcon.SMART_ROUTING -> iconSmartRouting
            }

            iconView.isVisible = true
            iconView.setColorFilter(color)
        }
    }

    private fun updatePartnerIconsIfNeeded() {
        if (currentPartners != partners) {
            currentPartners = partners
            partnerIcons().forEach {
                removeView(it)
            }
            partners.forEach { addPartnerIcon(it) }
        }
    }

    private fun updatePartnerClickListener() {
        partnerIcons().forEach {
            it.setOnClickListener(partnerIconClickListener)
        }
    }

    private fun addPartnerIcon(partner: Partner) {
        val imageView = ImageView(context)
        Glide.with(imageView).load(partner.iconUrl)
            .into(imageView)

        val padding = 4.toPx()
        with(imageView) {
            setOnClickListener(partnerIconClickListener)
            setPadding(padding, padding, padding, padding)
            contentDescription = partner.name
            tag = PartnerIconTag
        }
        addView(imageView)
        imageView.updateLayoutParams<ViewGroup.LayoutParams> {
            height = 32.toPx()
            width = 32.toPx()
        }
    }

    // Both functions return an immutable list that isn't affected by changes to children.
    private fun featureIcons() = children.filter { it.tag != PartnerIconTag }.toList()
    private fun partnerIcons() = children.filter { it.tag == PartnerIconTag }.toList()
}
