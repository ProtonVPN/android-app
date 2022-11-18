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
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.forEach
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ItemServerFeaturesAndButtonsBinding
import com.protonvpn.android.models.vpn.Partner
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.ui.ServerLoadColor
import com.protonvpn.android.ui.home.InformationActivity
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.setColorTint
import com.protonvpn.android.utils.setMinSizeTouchDelegate
import kotlin.math.ceil
import kotlin.properties.Delegates

/**
 * Implements the common part of server and country item row, the one with features, server load and buttons.
 */
class ServerRowFeaturesAndButtonsView : LinearLayout {

    private val binding = ItemServerFeaturesAndButtonsBinding.inflate(LayoutInflater.from(context), this)

    var featuresEnabled: Boolean by Delegates.observable(true) { _, _, _ -> update() }
    var featureKeywords: Collection<Server.Keyword>
        get() = binding.serverFeatures.keywords
        set(value) { binding.serverFeatures.keywords = value }

    var serverLoadEnabled: Boolean by Delegates.observable(true) { _, _, _ -> update() }
    var serverLoad: Float by Delegates.observable(0f) { _, _, _ -> update() }
    var isOnline: Boolean by Delegates.observable(true) { _, _, _ -> update() }
    var userHasAccess: Boolean by Delegates.observable(true) { _, _, _ -> update() }
    var isConnected: Boolean by Delegates.observable(false) { _, _, _ -> update() }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        with(binding) {
            buttonConnect.setMinSizeTouchDelegate()
            textLoad.minWidth = ceil(
                textLoad.paint.measureText(textLoad.resources.getString(R.string.serverLoad, "100"))
            ).toInt()
        }
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    )

    fun update() = with(binding) {
        val loadVisibility = when {
            !serverLoadEnabled -> View.GONE
            userHasAccess && isOnline -> View.VISIBLE
            userHasAccess -> View.INVISIBLE
            else -> View.GONE
        }
        textLoad.visibility = loadVisibility
        textLoad.text =
            textLoad.resources.getString(R.string.serverLoad, serverLoad.toInt().toString())

        serverLoadColor.visibility = loadVisibility
        serverLoadColor.setColorTint(ServerLoadColor.getColor(serverLoadColor, serverLoad))

        serverFeatures.isVisible = featuresEnabled && userHasAccess
        val featuresColorAttr =
            if (isOnline && userHasAccess) R.attr.proton_icon_norm else R.attr.proton_icon_disabled
        serverFeatures.color = MaterialColors.getColor(serverFeatures, featuresColorAttr)
        serverFeatures.keywords = featureKeywords

        buttonConnect.isOn = isConnected
        buttonConnect.isVisible = userHasAccess && isOnline
        buttonUpgrade.isVisible = !userHasAccess
        imageMaintenance.isVisible = userHasAccess && !isOnline
    }

    fun setPartnership(partner: List<Partner>, serverId: String) {
        partner.forEach {
            binding.serverFeatures.addPartnership(it) {
                context.startActivity(
                    InformationActivity.createIntent(context, InformationActivity.InfoType.Partners.Server(serverId))
                )
            }
        }
    }

    fun setPowerButtonListener(clickListener: OnClickListener) =
        binding.buttonConnect.setOnClickListener(clickListener)

    fun setPowerButtonContentDescription(contentDescription: CharSequence) {
        binding.buttonConnect.contentDescription = contentDescription
    }

    fun setUpgradeButtonListener(clickListener: OnClickListener) =
        binding.buttonUpgrade.setOnClickListener(clickListener)
}
