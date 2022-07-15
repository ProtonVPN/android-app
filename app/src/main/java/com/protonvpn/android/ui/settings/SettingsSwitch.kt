/*
 * Copyright (c) 2021. Proton Technologies AG
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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.TouchDelegate
import android.widget.Checkable
import android.widget.CompoundButton
import androidx.core.content.res.use
import com.protonvpn.android.R
import com.protonvpn.android.databinding.SettingsSwitchBinding
import kotlinx.parcelize.Parcelize

class SettingsSwitch : SettingsItemBase<SettingsSwitchBinding>, Checkable {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
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

    private fun init(attrs: AttributeSet?) {
        if (attrs != null) {
            context.theme.obtainStyledAttributes(attrs, R.styleable.SettingsSwitch, 0, 0).use {
                isChecked = it.getBoolean(R.styleable.SettingsSwitch_android_checked, true)
            }
        }
    }

    var switchClickInterceptor by binding.switchButton::switchClickInterceptor
    val switchView = binding.switchButton

    private var isCheckedRestored: Boolean? = null

    override fun inflate(context: Context): SettingsSwitchBinding =
        SettingsSwitchBinding.inflate(LayoutInflater.from(context), this)

    override fun textTitle() = binding.switchButton
    override fun textInfo() = binding.textInfo
    override fun dividerBelow() = binding.dividerBelow

    override fun setChecked(checked: Boolean) {
        binding.switchButton.isChecked = checked
    }

    override fun isChecked(): Boolean = binding.switchButton.isChecked

    override fun toggle() {
        binding.switchButton.toggle()
    }

    fun setOnCheckedChangeListener(listener: CompoundButton.OnCheckedChangeListener) {
        binding.switchButton.setOnCheckedChangeListener(listener)
    }

    fun setOnCheckedChangeListener(listener: (CompoundButton, Boolean) -> Unit) {
        binding.switchButton.setOnCheckedChangeListener(listener)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        binding.switchButton.isEnabled = enabled
    }

    @SuppressLint("DrawAllocation")
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        touchDelegate = TouchDelegate(Rect(0, 0, width, height), binding.switchButton)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Restore proper state after the default restoration.
        isCheckedRestored?.let { isChecked = it }
    }

    override fun onSaveInstanceState(): Parcelable? {
        return SavedState(
            super.onSaveInstanceState(),
            isChecked
        )
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val savedState = state as? SavedState
        super.onRestoreInstanceState(savedState?.superState)
        isCheckedRestored = savedState?.isChecked
    }

    @Parcelize
    data class SavedState(val superState: Parcelable?, val isChecked: Boolean) : Parcelable
}
