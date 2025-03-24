/*
 * Copyright (c) 2018 Proton AG
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
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.protonvpn.android.databinding.StreamingIconBinding
import com.protonvpn.android.servers.StreamingService
import com.protonvpn.android.utils.addListener

class StreamingIcon(context: Context, attributeSet: AttributeSet? = null) : FrameLayout(context, attributeSet) {

    private val binding: StreamingIconBinding

    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        binding = StreamingIconBinding.inflate(inflater, this, true)
    }

    fun addStreamingView(streamingService: StreamingService) {
        loadIcon(streamingService.name, streamingService.iconUrl)
    }

    fun addStreamingView(name: String, @DrawableRes drawableRes: Int? = null) {
        loadIcon(name, drawableRes)
    }

    private fun loadIcon(name: String, iconDrawable: Any?) = with(binding) {
        if (iconDrawable != null) {
            Glide.with(icon).load(iconDrawable).addListener(
                onFail = {
                    fallbackToStreamingServiceName(name)
                })
                .into(icon)
        } else {
            fallbackToStreamingServiceName(name)
        }
    }

    private fun StreamingIconBinding.fallbackToStreamingServiceName(name: String) {
        icon.isVisible = false
        text.isVisible = true
        text.text = name
    }
}
