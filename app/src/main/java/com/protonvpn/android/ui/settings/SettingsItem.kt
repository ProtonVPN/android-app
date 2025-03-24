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

package com.protonvpn.android.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.protonvpn.android.databinding.SettingsItemBinding

class SettingsItem : SettingsItemBase<SettingsItemBinding> {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun inflate(context: Context): SettingsItemBinding =
        SettingsItemBinding.inflate(LayoutInflater.from(context), this)

    override fun textTitle() = binding.textTitle
    override fun textInfo() = binding.textInfo
    override fun dividerBelow() = binding.dividerBelow

    fun setValue(text: CharSequence) {
        setTextAndVisibility(binding.textValue, text)
    }
}
