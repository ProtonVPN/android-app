/*
 * Copyright (c) 2021. Proton AG
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
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ConnectionFlagsViewBinding
import com.protonvpn.android.servers.Server
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.utils.AndroidUtils.getFloatRes
import com.protonvpn.android.utils.CountryTools

class CountryWithFlagsView : LinearLayout {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initAttrs(attrs)
    }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initAttrs(attrs)
    }

    val binding = ConnectionFlagsViewBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = HORIZONTAL
    }

    private fun initAttrs(attrs: AttributeSet?) {
        context.withStyledAttributes(attrs, R.styleable.CountryWithFlagsView) {
            with(binding) {
                TextViewCompat.setTextAppearance(
                    textCountry,
                    getResourceId(R.styleable.CountryWithFlagsView_android_textAppearance, -1)
                )
                val flagWidth = getDimensionPixelSize(R.styleable.CountryWithFlagsView_flagWidth, -1)
                if (flagWidth != -1) {
                    imageEntryCountry.layoutParams.width = flagWidth
                    imageExitCountry.layoutParams.width = flagWidth
                }
                val flagMarginEnd = getDimensionPixelSize(R.styleable.CountryWithFlagsView_flagMarginEnd, -1)
                if (flagMarginEnd != -1) {
                    arrayOf(imageEntryCountry, imageExitCountry, imageSCArrow).forEach {
                        it.updateLayoutParams<MarginLayoutParams> { marginEnd = flagMarginEnd }
                    }
                }
            }
        }
    }

    fun setCountry(server: Server, text: CharSequence? = null) {
        with(server) {
            update(exitCountry, entryCountry.takeIf { isSecureCoreServer }, text)
        }
    }

    fun setCountry(vpnCountry: VpnCountry, text: CharSequence? = null) {
        update(vpnCountry.flag, null, text)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        with(binding) {
            val flagAlpha = if (enabled) 1f else resources.getFloatRes(R.dimen.inactive_flag_alpha)
            textCountry.isEnabled = enabled
            imageEntryCountry.alpha = flagAlpha
            imageExitCountry.alpha = flagAlpha
        }
    }

    private fun update(exitCountryCode: String, entryCountryCode: String?, text: CharSequence? = null) {
        with(binding) {
            val isSecureCore = entryCountryCode != null
            imageEntryCountry.isVisible = isSecureCore
            imageSCArrow.isVisible = isSecureCore
            if (entryCountryCode != null) {
                imageEntryCountry.setFlag(entryCountryCode)
            }
            imageExitCountry.setFlag(exitCountryCode)
            textCountry.text = text ?: CountryTools.getFullName(exitCountryCode)
        }
    }

    private fun ImageView.setFlag(flag: String) =
        setImageResource(CountryTools.getFlagResource(context, flag))
}
