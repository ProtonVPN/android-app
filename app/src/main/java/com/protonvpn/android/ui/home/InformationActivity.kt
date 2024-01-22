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
package com.protonvpn.android.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.flexbox.FlexboxLayout
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.StreamingIcon
import com.protonvpn.android.databinding.ActivityInformationBinding
import com.protonvpn.android.databinding.InfoHeaderBinding
import com.protonvpn.android.databinding.InfoItemBinding
import com.protonvpn.android.databinding.InfoServerLoadBinding
import com.protonvpn.android.databinding.StreamingInfoBinding
import com.protonvpn.android.models.vpn.Partner
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ViewUtils.toPx
import com.protonvpn.android.utils.ViewUtils.viewBinding
import com.protonvpn.android.utils.setTextOrGoneIfNullOrEmpty
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.proton.core.presentation.utils.onClick
import me.proton.core.presentation.utils.openBrowserLink
import me.proton.core.presentation.R as CoreR

@AndroidEntryPoint
class InformationActivity : BaseActivityV2() {

    sealed class InfoType : Parcelable {
        @Parcelize
        object Generic : InfoType()
        @Parcelize
        data class Streaming(val countryCode: String) : InfoType()
        @Parcelize
        object Gateways : InfoType()

        sealed class Partners : InfoType() {
            @Parcelize
            data class Server(val serverId: String) : Partners()

            @Parcelize
            data class Country(val countryCode: String, val secureCore: Boolean) : Partners()
        }

        companion object {
            @JvmField
            val generic: InfoType = Generic
        }
    }

    private val binding by viewBinding(ActivityInformationBinding::inflate)
    private val viewModel: InformationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initToolbarWithUpEnabled(binding.appbar.toolbar)

        val info = intent.getParcelableExtra<InfoType>(EXTRA_INFO_TYPE)
        when(info) {
            is InfoType.Generic -> setupGenericInfo()
            is InfoType.Streaming -> setupStreamingInfo(info.countryCode)
            is InfoType.Partners -> setupPartnershipInfo(info)
            is InfoType.Gateways -> setupGatewaysInfo()
            null -> Unit
        }
    }

    private fun setupGenericInfo() {
        addHeader(R.string.info_features)
        addItem(CoreR.drawable.ic_proton_globe, R.string.smart_routing_title, R.string.smart_routing_description,
            Constants.SMART_ROUTING_INFO_URL)
        addItem(CoreR.drawable.ic_proton_play, R.string.streaming_title, R.string.streaming_description,
            Constants.STREAMING_INFO_URL)
        addItem(CoreR.drawable.ic_proton_arrows_switch, R.string.p2p_title, R.string.p2p_description,
            Constants.P2P_INFO_URL)
        addItem(CoreR.drawable.ic_proton_brand_tor, R.string.tor_title, R.string.tor_description,
            Constants.TOR_INFO_URL)

        addHeader(R.string.info_performance)
        addItem(CoreR.drawable.ic_proton_servers, R.string.server_load_title, R.string.server_load_description,
            Constants.SERVER_LOAD_INFO_URL, customViewProvider = this::createServerLoadCustomView)
    }

    private fun setupPartnershipInfo(infoType: InfoType.Partners) {
        lifecycleScope.launch {
            val partners = when (infoType) {
                is InfoType.Partners.Server -> viewModel.getPartnersForServer(infoType.serverId)
                is InfoType.Partners.Country ->
                    viewModel.getPartnersForCountry(infoType.countryCode, infoType.secureCore)
            }
            if (partners != null) {
                setupPartnershipInfo(partners)
            } else {
                snackbarHelper.errorSnack(R.string.something_went_wrong)
                finish()
            }
        }
    }

    private fun setupPartnershipInfo(partners: List<Partner>) {
        title = getString(R.string.activity_information_title)

        addItem(CoreR.drawable.ic_proton_servers, R.string.partnership_free_title, R.string.partnership_free_description)
        viewModel.getPartnerTypes().forEach {
            addItem(it.iconUrl, it.type, it.description)
        }
        addHeader(R.string.partnership_partners_title)
        partners.forEach { addItem(it.iconUrl, it.name, it.description) }
    }

    private fun setupStreamingInfo(country: String) {
        val countryName = CountryTools.getFullName(country)
        title = getString(R.string.activity_information_plus_title, countryName)

        addHeader(R.string.info_features)
        addItem(CoreR.drawable.ic_proton_play, 0, R.string.streaming_services_description,
            Constants.STREAMING_INFO_URL,
            titleString = getString(R.string.streaming_title_with_country, countryName),
            customViewProvider = { parent -> createStreamingServicesCustomView(country, parent) })
    }

    private fun setupGatewaysInfo() {
        addItem(
            iconRes = CoreR.drawable.ic_proton_servers,
            titleRes = R.string.activity_information_gateways_title,
            descriptionRes = R.string.info_gateways_description,
            url = Constants.DEDICATED_IPS_INFO_URL
        )
    }

    private fun createServerLoadCustomView(parent: ViewGroup) =
        InfoServerLoadBinding.inflate(LayoutInflater.from(binding.root.context), parent, false).root

    private fun createStreamingServicesCustomView(country: String, parent: ViewGroup): View {
        val flexbox =
            StreamingInfoBinding.inflate(LayoutInflater.from(binding.root.context), parent, false)
                .streamingIconsContainer

        val dimStreamingIcons = !viewModel.isPlusUser()
        viewModel.getStreamingServices(country).let { services ->
            for (service in services) {
                val icon = StreamingIcon(this)
                if (dimStreamingIcons)
                    icon.alpha = STREAMING_ICON_DIM_ALPHA
                icon.layoutParams = FlexboxLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    val margin = STREAMING_ICON_MARGINS
                    setMargins(margin, margin, margin, margin)
                }
                icon.addStreamingView(service)
                flexbox.addView(icon)
            }
        }
        return flexbox
    }

    private fun addHeader(@StringRes label: Int) {
        val list = binding.content.listLayout
        val headerBinding = InfoHeaderBinding.inflate(LayoutInflater.from(list.context), list, false)
        headerBinding.label.text = getString(label)
        list.addView(headerBinding.root)
    }

    private fun addItem(
        iconUrl: String?,
        titleString: String?,
        descriptionString: String?,
    ) {
        val list = binding.content.listLayout
        val infoBinding = InfoItemBinding.inflate(LayoutInflater.from(list.context), list, false)
        with(infoBinding) {
            Glide.with(icon).load(iconUrl)
                .into(icon)

            title.setTextOrGoneIfNullOrEmpty(titleString)
            description.setTextOrGoneIfNullOrEmpty(descriptionString)
        }
        list.addView(infoBinding.root)
    }

    private fun addItem(
        @DrawableRes iconRes: Int,
        @StringRes titleRes: Int,
        @StringRes descriptionRes: Int,
        url: String? = null,
        titleString: String? = null,
        customViewProvider: ((parent: ViewGroup) -> View)? = null
    ) {
        val list = binding.content.listLayout
        val infoBinding = InfoItemBinding.inflate(LayoutInflater.from(list.context), list, false)
        with(infoBinding) {
            icon.setImageResource(iconRes)
            title.text = titleString ?: getString(titleRes)
            description.setText(descriptionRes)
            url?.let {
                learnMore.isVisible = true
                learnMore.onClick { openBrowserLink(url) }
            }

            if (customViewProvider != null) {
                customViewContainer.isVisible = true
                customViewContainer.addView(customViewProvider(customViewContainer))
            }
        }
        list.addView(infoBinding.root)
    }

    companion object {
        private const val EXTRA_INFO_TYPE = "EXTRA_INFO_TYPE"
        private val STREAMING_ICON_MARGINS = 8.toPx()
        private const val STREAMING_ICON_DIM_ALPHA = 0.3f

        @JvmStatic
        fun createIntent(context: Context, infoType: InfoType) =
            Intent(context, InformationActivity::class.java).apply {
                putExtra(EXTRA_INFO_TYPE, infoType)
            }
    }
}
