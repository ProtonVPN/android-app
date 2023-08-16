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

package com.protonvpn.android.search

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.style.TextAppearanceSpan
import android.view.View
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import com.protonvpn.android.R
import com.protonvpn.android.components.ServerRowFeaturesAndButtonsView
import com.protonvpn.android.components.featureIcons
import com.protonvpn.android.databinding.ItemHeaderSearchRecentsBinding
import com.protonvpn.android.databinding.ItemSearchRecentBinding
import com.protonvpn.android.databinding.ItemSearchResultCountryBinding
import com.protonvpn.android.databinding.ItemSearchResultTwoLineBinding
import com.protonvpn.android.databinding.ItemSearchUpgradeBannerBinding
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.utils.CountryTools
import com.xwray.groupie.viewbinding.BindableItem

abstract class SearchResultBinding<Value, Binding : ViewBinding>(
    @LayoutRes private val layoutId: Int,
    protected val item: SearchViewModel.ResultItem<Value>,
    private val matchLength: Int,
    private val onConnect: (SearchViewModel.ResultItem<Value>) -> Unit,
    private val onDisconnect: () -> Unit,
    private val onUpgrade: () -> Unit
) : BindableItem<Binding>(item.match.value.hashCode().toLong()) {

    protected fun bindFeaturesAndButtons(
        featuresButtons: ServerRowFeaturesAndButtonsView,
        serverId: String? = null,
        showFeatures: Boolean = false,
        showServerLoad: Boolean = false,
    ) {
        with(featuresButtons) {
            featuresEnabled = showFeatures
            serverId?.let { setPartnership(item.partnerships, serverId) }
            serverLoadEnabled = showServerLoad
            userHasAccess = item.hasAccess
            isConnected = item.isConnected
            isOnline = item.isOnline

            val powerButtonListener = View.OnClickListener {
                if (!item.isConnected) onConnect(item)
                else onDisconnect()
            }
            setPowerButtonListener(powerButtonListener)
            setUpgradeButtonListener { onUpgrade() }
        }
    }

    protected fun bindFeaturesAndButtons(featuresButtons: ServerRowFeaturesAndButtonsView, server: Server) {
        bindFeaturesAndButtons(featuresButtons, server.serverId, showFeatures = true, showServerLoad = true)
        with(featuresButtons) {
            featureIcons = server.featureIcons()
            serverLoad = server.load
        }
    }

    protected fun getMatchTextWithHighlight(context: Context) = with(item.match) {
        SpannableStringBuilder(text).apply {
            setSpan(TextAppearanceSpan(context, R.style.Proton_Text_Default), index, index + matchLength, 0)
        }
    }

    override fun getViewType(): Int = this::class.hashCode()

    override fun getLayout(): Int = layoutId
}

class CountryResultBinding(
    item: SearchViewModel.ResultItem<VpnCountry>,
    matchLength: Int,
    onConnect: (SearchViewModel.ResultItem<VpnCountry>) -> Unit,
    onDisconnect: () -> Unit,
    onUpgrade: () -> Unit
) : SearchResultBinding<VpnCountry, ItemSearchResultCountryBinding>(
    R.layout.item_search_result_country, item, matchLength, onConnect, onDisconnect, onUpgrade
) {

    override fun bind(binding: ItemSearchResultCountryBinding, position: Int) {
        with(binding) {
            bindFeaturesAndButtons(featuresAndButtons, showFeatures = true)

            val country = item.match.value
            countryWithFlag.setCountry(country, getMatchTextWithHighlight(root.context))
            with(featuresAndButtons) {
                featureIcons = country.featureIcons()
            }
        }
    }

    override fun initializeViewBinding(view: View) = ItemSearchResultCountryBinding.bind(view)
}

class CityResultBinding(
    item: SearchViewModel.ResultItem<List<Server>>,
    matchLength: Int,
    onConnect: (SearchViewModel.ResultItem<List<Server>>) -> Unit,
    onDisconnect: () -> Unit,
    onUpgrade: () -> Unit
) : SearchResultBinding<List<Server>, ItemSearchResultTwoLineBinding>(
    R.layout.item_search_result_two_line, item, matchLength, onConnect, onDisconnect, onUpgrade
) {

    override fun bind(binding: ItemSearchResultTwoLineBinding, position: Int) {
        with(binding) {
            val servers = item.match.value
            bindFeaturesAndButtons(featuresAndButtons, showFeatures = true)
            with(featuresAndButtons) {
                featureIcons = servers.flatMapTo(mutableSetOf()) { it.featureIcons() }
            }

            val context = root.context
            val flag = servers.first().exitCountry
            imageFlag.setImageResource(CountryTools.getFlagResource(context, flag))
            textTitle.text = getMatchTextWithHighlight(context)
            textSubtitle.text = CountryTools.getFullName(flag)
        }
    }

    override fun initializeViewBinding(view: View) = ItemSearchResultTwoLineBinding.bind(view)
}

class ServerResultBinding(
    item: SearchViewModel.ResultItem<Server>,
    matchLength: Int,
    onConnect: (SearchViewModel.ResultItem<Server>) -> Unit,
    onDisconnect: () -> Unit,
    onUpgrade: () -> Unit
) : SearchResultBinding<Server, ItemSearchResultTwoLineBinding>(
    R.layout.item_search_result_two_line, item, matchLength, onConnect, onDisconnect, onUpgrade
) {
    override fun bind(binding: ItemSearchResultTwoLineBinding, position: Int) {
        with(binding) {
            val server = item.match.value
            bindFeaturesAndButtons(featuresAndButtons, server)

            val context = root.context
            textTitle.text = getMatchTextWithHighlight(context)
            textSubtitle.text = server.displayCity
        }
    }

    override fun initializeViewBinding(view: View) = ItemSearchResultTwoLineBinding.bind(view).apply {
        imageFlag.isVisible = false
    }
}

class RecentResultBinding(
    private val item: String,
    private val onClick: (String) -> Unit,
) : BindableItem<ItemSearchRecentBinding>(item.hashCode().toLong()) {
    override fun bind(binding: ItemSearchRecentBinding, position: Int) {
        with(binding) {
            title.text = item
            root.setOnClickListener { onClick(item) }
        }
    }

    override fun initializeViewBinding(view: View) = ItemSearchRecentBinding.bind(view)
    override fun getLayout(): Int = R.layout.item_search_recent
}

data class RecentsHeaderViewHolder(
    @StringRes private val textRes: Int = 0,
    private val text: String? = null,
    private val itemId: Long = 1,
    private val onClear: () -> Unit
) : BindableItem<ItemHeaderSearchRecentsBinding>(itemId) {

    override fun bind(viewBinding: ItemHeaderSearchRecentsBinding, position: Int) {
        if (text != null) {
            viewBinding.textHeader.text = text
        } else {
            viewBinding.textHeader.setText(textRes)
        }
        viewBinding.textClear.setOnClickListener { onClear() }
    }

    override fun initializeViewBinding(view: View): ItemHeaderSearchRecentsBinding =
        ItemHeaderSearchRecentsBinding.bind(view)

    override fun getLayout(): Int = R.layout.item_header_search_recents

    override fun isClickable(): Boolean = true
}

class SecureCoreServerResultBinding(
    item: SearchViewModel.ResultItem<Server>,
    matchLength: Int,
    onConnect: (SearchViewModel.ResultItem<Server>) -> Unit,
    onDisconnect: () -> Unit,
    onUpgrade: () -> Unit
) : SearchResultBinding<Server, ItemSearchResultCountryBinding>(
    R.layout.item_search_result_country, item, matchLength, onConnect, onDisconnect, onUpgrade
) {
    override fun bind(binding: ItemSearchResultCountryBinding, position: Int) {
        with(binding) {
            val server = item.match.value
            bindFeaturesAndButtons(featuresAndButtons, server)
            countryWithFlag.setCountry(server, getMatchTextWithHighlight(root.context))
        }
    }

    override fun initializeViewBinding(view: View) = ItemSearchResultCountryBinding.bind(view)
}

class UpgradeBannerItem(
    private val countryCount: Int,
    private val onClick: () -> Unit
) : BindableItem<ItemSearchUpgradeBannerBinding>(1) {
    override fun bind(binding: ItemSearchUpgradeBannerBinding, position: Int) = with(binding) {
        val resources = root.resources
        textMessage.text = resources.getQuantityString(
            R.plurals.search_upsell_banner_message, countryCount, countryCount
        )
        root.setOnClickListener { onClick() }
    }

    override fun getLayout(): Int = R.layout.item_search_upgrade_banner
    override fun initializeViewBinding(view: View) = ItemSearchUpgradeBannerBinding.bind(view)
}
