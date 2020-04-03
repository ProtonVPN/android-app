/*
 * Copyright (c) 2020 Proton Technologies AG
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

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ItemProtocolSelectionBinding
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol

class ProtocolSelection @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val binding: ItemProtocolSelectionBinding = DataBindingUtil.inflate(
        LayoutInflater.from(context), R.layout.item_protocol_selection, this, true)

    var useSmart: Boolean = true
        private set

    lateinit var manualProtocol: VpnProtocol
        private set

    lateinit var transmissionProtocol: TransmissionProtocol
        private set

    val protocol get() = if (useSmart) VpnProtocol.Smart else manualProtocol

    fun init(
        initialUseSmart: Boolean,
        initialManualProtocol: VpnProtocol,
        initialTransmissionProtocol: TransmissionProtocol,
        changeCallback: () -> Unit
    ) = with(binding) {
        useSmart = initialUseSmart
        manualProtocol = initialManualProtocol
        transmissionProtocol = initialTransmissionProtocol

        smartProtocolSwitch.switchProton.isChecked = useSmart
        smartProtocolSwitch.switchProton.setOnCheckedChangeListener { _, isChecked ->
            useSmart = isChecked
            update()
            changeCallback()
        }

        spinnerDefaultProtocol.setItems(listOf(
                ListableString(VpnProtocol.IKEv2.toString()),
                ListableString(VpnProtocol.OpenVPN.toString())))
        spinnerDefaultProtocol.setOnItemSelectedListener { item, _ ->
            manualProtocol = VpnProtocol.valueOf(item.getLabel(context))
            update()
            changeCallback()
        }

        spinnerTransmissionProtocol.setItems(
                TransmissionProtocol.values().map { ListableString(it.name) })
        spinnerTransmissionProtocol.setOnItemSelectedListener { item, _ ->
            transmissionProtocol = TransmissionProtocol.valueOf(item.getLabel(context))
            changeCallback()
        }

        update()
    }

    private fun update() = with(binding) {
        manualProtocolLayout.isVisible = !useSmart
        layoutTransmissionProtocol.isVisible = !useSmart && manualProtocol == VpnProtocol.OpenVPN
        spinnerDefaultProtocol.setSelectedItem(ListableString(manualProtocol.toString()))
        spinnerTransmissionProtocol.setSelectedItem(ListableString(transmissionProtocol.toString()))
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setTouchBlocker(touchListener: (View, MotionEvent) -> Boolean) = with(binding) {
        spinnerTransmissionProtocol.setOnTouchListener(touchListener)
        spinnerDefaultProtocol.setOnTouchListener(touchListener)
        smartProtocolSwitch.switchProton.setOnTouchListener(touchListener)
    }

    private class ListableString(private val name: String) : Listable {
        override fun getLabel(context: Context) = name
    }
}
