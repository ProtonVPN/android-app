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

package com.protonvpn.android.ui.promooffers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.TextAppearanceSpan
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.ApiNotificationOfferFeature
import com.protonvpn.android.appconfig.ApiNotificationOfferPanel
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityPromoOfferBinding
import com.protonvpn.android.databinding.ItemPromoFeatureBinding
import com.protonvpn.android.utils.openUrl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val INCENTIVE_PRICE_PLACEHOLDER = "%IncentivePrice%"

@AndroidEntryPoint
class PromoOfferActivity : BaseActivityV2() {

    private val viewModel: PromoOfferViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityPromoOfferBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        initToolbarWithUpEnabled(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val offerId = getOfferId(intent)
        lifecycleScope.launch {
            val panel = offerId?.let { viewModel.getPanel(offerId) }
            if (panel != null) {
                setViews(binding, panel)
            } else {
                Toast.makeText(this@PromoOfferActivity, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun setViews(binding: ActivityPromoOfferBinding, panel: ApiNotificationOfferPanel) {
        with(binding) {
            textIncentive.setTextOrGoneIfNull(createIncentiveText(panel.incentive, panel.incentivePrice))
            textPill.setTextOrGoneIfNull(panel.pill)
            textTitle.setTextOrGoneIfNull(panel.title)
            textFooter.setTextOrGoneIfNull(panel.pageFooter)

            addFeatures(layoutFeatures, panel.features, panel.featuresFooter)

            if (panel.pictureUrl != null) {
                val activity = this@PromoOfferActivity
                val maxSize = getPictureMaxSize(activity)
                Glide.with(activity)
                    .load(panel.pictureUrl)
                    // Make sure the size is the same as for preload.
                    .override(maxSize.width, maxSize.height)
                    .into(imagePicture)
            }

            if (panel.button != null) {
                buttonOpenOffer.visibility = View.VISIBLE
                buttonOpenOffer.text = panel.button.text
                buttonOpenOffer.setOnClickListener { openUrl(panel.button.url) }
            }
        }
    }

    private fun addFeatures(layout: ViewGroup, features: List<ApiNotificationOfferFeature>?, footer: String?) {
        features
            ?.forEach { addFeatureLine(layout, it) }
            ?.also { layout.visibility = View.VISIBLE }

        if (footer != null) {
            val featureFooterViews =
                ItemPromoFeatureBinding.inflate(LayoutInflater.from(this), layout, true)
            featureFooterViews.text.text = footer
            featureFooterViews.text.setTextAppearance(R.style.Proton_Text_Caption_Weak)
            layout.visibility = View.VISIBLE
        }
    }

    private fun addFeatureLine(container: ViewGroup, feature: ApiNotificationOfferFeature) {
        val views = ItemPromoFeatureBinding.inflate(LayoutInflater.from(this), container, true)
        views.text.text = feature.text
        Glide.with(this)
            .load(feature.iconUrl)
            .placeholder(R.drawable.ic_proton_checkmark)
            .error(R.drawable.ic_proton_checkmark)
            .into(views.imageIcon)
    }

    private fun createIncentiveText(incentiveTemplate: String?, price: String?): CharSequence? {
        if (incentiveTemplate == null) return null

        // Protect the price text part from being broken into lines.
        val nonBreakingPrice = price?.replace(' ', '\u00a0')
        val nonBreakingTemplate = incentiveTemplate.replace("/", "/\u2060")

        val placeholderIndex = nonBreakingTemplate.indexOf(INCENTIVE_PRICE_PLACEHOLDER)
        return if (placeholderIndex != -1 && nonBreakingPrice != null) {
            val richText = SpannableString(nonBreakingTemplate.replace(INCENTIVE_PRICE_PLACEHOLDER, nonBreakingPrice))
            val priceSpan = TextAppearanceSpan(this, R.style.Proton_Text_Hero)
            richText.setSpan(priceSpan, placeholderIndex, placeholderIndex + nonBreakingPrice.length, 0)
            richText
        } else {
            nonBreakingTemplate
        }
    }

    // TODO: put it in a common place?
    private fun TextView.setTextOrGoneIfNull(newText: CharSequence?) {
        if (newText != null) {
            visibility = View.VISIBLE
            text = newText
        } else {
            visibility = View.GONE
        }
    }

    companion object {
        private const val EXTRA_OFFER_ID = "id"

        @JvmStatic
        fun createIntent(context: Context, offerId: String) =
            Intent(context, PromoOfferActivity::class.java).apply {
                putExtra(EXTRA_OFFER_ID, offerId)
            }

        fun preloadPicture(context: Context, pictureUrl: String) {
            val maxSize = getPictureMaxSize(context)
            // Use the same dimensions for preload as for displaying the image.
            Glide.with(context).load(pictureUrl).preload(maxSize.width, maxSize.height)
        }

        private fun getOfferId(intent: Intent): String? = intent.getStringExtra(EXTRA_OFFER_ID)

        private fun getPictureMaxSize(context: Context) = with(context.resources) {
            Size(
                displayMetrics.widthPixels - 2 * getDimensionPixelSize(R.dimen.offer_panel_picture_horizontal_margin),
                getDimensionPixelSize(R.dimen.offer_panel_picture_max_height)
            )
        }
    }
}
