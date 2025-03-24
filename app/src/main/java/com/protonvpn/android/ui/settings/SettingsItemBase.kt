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
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.res.use
import androidx.core.view.isVisible
import com.protonvpn.android.R

abstract class SettingsItemBase<Binding : Any> : LinearLayout {
    constructor(context: Context) : super(context) {
        init(null)
    }
    constructor(context: Context, attrs: AttributeSet?) : super(
        context,
        attrs,
        0,
        R.style.SettingsItem
    ) {
        init(attrs)
    }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
        R.style.SettingsItem
    ) {
        init(attrs)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(attrs)
    }

    protected lateinit var binding: Binding

    private fun init(attrs: AttributeSet?) {
        binding = inflate(context)
        if (attrs != null) {
            context.theme.obtainStyledAttributes(attrs, R.styleable.SettingsItemBase, 0, 0).use {
                isEnabled = it.getBoolean(R.styleable.SettingsItemBase_android_enabled, true)
                textTitle().text = it.getString(R.styleable.SettingsItemBase_title)
                setTextAndVisibility(textInfo(), it.getString(R.styleable.SettingsItemBase_infoText))
                dividerBelow().isVisible =
                    it.getBoolean(R.styleable.SettingsItemBase_dividerBelow, true)
            }
        }
    }

    fun setInfoText(text: CharSequence?, hasLinks: Boolean = false) {
        setTextAndVisibility(textInfo(), text)
        if (hasLinks) {
            textInfo().movementMethod = LinkMovementMethod.getInstance()
        }
    }

    fun setInfoText(@StringRes textId: Int) {
        setTextAndVisibility(textInfo(), resources.getString(textId))
    }

    protected fun setTextAndVisibility(textView: TextView, text: CharSequence?) {
        if (text.isNullOrEmpty()) {
            textView.isVisible = false
        } else {
            textView.isVisible = true
            textView.text = text
        }
    }

    protected abstract fun inflate(context: Context): Binding
    protected abstract fun textTitle(): TextView
    protected abstract fun textInfo(): TextView
    protected abstract fun dividerBelow(): View
}
