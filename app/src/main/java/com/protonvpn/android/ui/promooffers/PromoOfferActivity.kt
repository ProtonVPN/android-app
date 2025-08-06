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

package com.protonvpn.android.ui.promooffers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.TextAppearanceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.ApiNotificationOfferButton
import com.protonvpn.android.appconfig.ApiNotificationOfferFeature
import com.protonvpn.android.appconfig.ApiNotificationOfferFullScreenImage
import com.protonvpn.android.appconfig.ApiNotificationOfferPanel
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityPromoOfferBinding
import com.protonvpn.android.databinding.ItemPromoFeatureBinding
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.ui.planupgrade.CarouselUpgradeDialogActivity
import com.protonvpn.android.utils.addListener
import com.protonvpn.android.utils.openUrl
import com.protonvpn.android.utils.setTextOrGoneIfNullOrEmpty
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.presentation.utils.doOnApplyWindowInsets
import me.proton.core.presentation.utils.viewBinding
import me.proton.core.presentation.R as CoreR

private const val INCENTIVE_PRICE_PLACEHOLDER = "%IncentivePrice%"
private const val FULL_SCREEN_IMAGE_AUTOSCROLL_DELAY_MS = 750L
private const val FULL_SCREEN_IMAGE_AUTOSCROLL_DURATION_MS = 750

@AndroidEntryPoint
class PromoOfferActivity : BaseActivityV2() {

    private val viewModel: PromoOfferViewModel by viewModels()
    private val binding by viewBinding(ActivityPromoOfferBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initToolbarWithUpEnabled(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val offerId = getOfferId(intent)
        lifecycleScope.launch {
            val panel = offerId?.let { viewModel.init(offerId) }
            if (panel != null) {
                setViews(panel)
            } else {
                Toast.makeText(this@PromoOfferActivity, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
                ProtonLogger.logCustom(LogLevel.ERROR, LogCategory.UI, "No notification with ID $offerId")
                finish()
            }
        }
        viewModel.openUrlEvent
            .onEach { url ->
                openUrl(url)
                finish()
            }
            .launchIn(lifecycleScope)
        viewModel.isLoading
            .onEach { isLoading -> with(binding.buttonOpenOffer) {
                if (isLoading) setLoading() else setIdle()
            }}
            .launchIn(lifecycleScope)
    }

    private fun setViews(panel: ApiNotificationOfferPanel) {
        if (panel.fullScreenImage != null) setFullScreenImage(panel.fullScreenImage)
        else setDetailedOffer(panel)

        if (panel.button != null) setButton(panel.button)
    }

    private fun setDetailedOffer(panel: ApiNotificationOfferPanel) {
        with(binding) {
            textIncentive.setTextOrGoneIfNullOrEmpty(createIncentiveText(panel.incentive, panel.incentivePrice))
            textPill.setTextOrGoneIfNullOrEmpty(panel.pill)
            textTitle.setTextOrGoneIfNullOrEmpty(panel.title)
            textFooter.setTextOrGoneIfNullOrEmpty(panel.pageFooter)

            addFeatures(layoutFeatures, panel.features, panel.featuresFooter)

            if (panel.pictureUrl != null) {
                val activity = this@PromoOfferActivity
                val maxSize = PromoOfferImage.getPictureMaxSize(activity)
                imagePicture.visibility = View.VISIBLE
                Glide.with(activity)
                    .load(panel.pictureUrl)
                    // Make sure the size is the same as for preload.
                    .override(maxSize.width, maxSize.height)
                    .into(imagePicture)
            }
        }
    }

    private fun addFeatures(layout: ViewGroup, features: List<ApiNotificationOfferFeature>?, footer: String?) {
        features
            ?.forEach { addFeatureLine(layout, it) }
            ?.also { layout.visibility = View.VISIBLE }

        if (!footer.isNullOrEmpty()) {
            val featureFooterViews =
                ItemPromoFeatureBinding.inflate(LayoutInflater.from(this), layout, true)
            featureFooterViews.text.text = footer
            featureFooterViews.text.setTextAppearance(CoreR.style.Proton_Text_Caption_Weak)
            layout.visibility = View.VISIBLE
        }
    }

    private fun addFeatureLine(container: ViewGroup, feature: ApiNotificationOfferFeature) {
        val views = ItemPromoFeatureBinding.inflate(LayoutInflater.from(this), container, true)
        views.text.text = feature.text
        Glide.with(this)
            .load(feature.iconUrl)
            .placeholder(CoreR.drawable.ic_proton_checkmark)
            .error(CoreR.drawable.ic_proton_checkmark)
            .into(views.imageIcon)
    }

    private fun createIncentiveText(incentiveTemplate: String?, price: String?): CharSequence? {
        if (incentiveTemplate == null || incentiveTemplate.isEmpty()) return null

        // Protect the price text part from being broken into lines.
        val nonBreakingPrice = price?.replace(' ', '\u00a0')
        val nonBreakingTemplate = incentiveTemplate.replace("/", "/\u2060")

        val placeholderIndex = nonBreakingTemplate.indexOf(INCENTIVE_PRICE_PLACEHOLDER)
        return if (placeholderIndex != -1 && nonBreakingPrice != null) {
            val richText = SpannableString(nonBreakingTemplate.replace(INCENTIVE_PRICE_PLACEHOLDER, nonBreakingPrice))
            val priceSpan = TextAppearanceSpan(this, CoreR.style.Proton_Text_Hero)
            richText.setSpan(priceSpan, placeholderIndex, placeholderIndex + nonBreakingPrice.length, 0)
            richText
        } else {
            nonBreakingTemplate
        }
    }

    private fun setFullScreenImage(imageSpec: ApiNotificationOfferFullScreenImage) {
        with(binding) {
            layoutContent.setPadding(0, 0, 0, 0) // Full screen image is edge-to-edge.
            makeTransparentStatusBar(root, toolbar)

            val imageUrl = PromoOfferImage.getFullScreenImageUrl(this@PromoOfferActivity, imageSpec)

            Glide.with(this@PromoOfferActivity)
                .load(imageUrl)
                .optionalCenterInside()
                .addListener(onSuccess = {
                    imageFullScreen.visibility = View.VISIBLE
                    imageFullScreen.contentDescription = imageSpec.alternativeText
                    scheduleScrollDown()
                }, onFail = {
                    with(textFullScreenImageAlternative) {
                        visibility = View.VISIBLE
                        text = imageSpec.alternativeText
                    }
                })
                .into(imageFullScreen)
        }
    }

    private fun setButton(button: ApiNotificationOfferButton) {
        with(binding.buttonOpenOffer) {
            visibility = View.VISIBLE
            text = button.text
            setOnClickListener { viewModel.onOpenOfferClicked() }
        }
    }

    private fun makeTransparentStatusBar(root: View, toolbar: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root.doOnApplyWindowInsets { view, insets, initialMargin, _ ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = initialMargin.top
                bottomMargin =
                    insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom +
                        initialMargin.bottom
            }
            toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin =
                    insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top + initialMargin.top
            }
        }
    }

    private fun scheduleScrollDown() {
        lifecycleScope.launchWhenResumed {
            delay(FULL_SCREEN_IMAGE_AUTOSCROLL_DELAY_MS)
            with(binding) {
                if (scroll.canScrollVertically(1) && scroll.scrollY == 0) {
                    scroll.smoothScrollTo(
                        0, layoutContent.height - scroll.height, FULL_SCREEN_IMAGE_AUTOSCROLL_DURATION_MS
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_OFFER_ID = "id"

        @JvmStatic
        fun createIntent(context: Context, offerId: String) =
            Intent(context, PromoOfferActivity::class.java).apply {
                putExtra(EXTRA_OFFER_ID, offerId)
            }

        private fun getOfferId(intent: Intent): String? = intent.getStringExtra(EXTRA_OFFER_ID)
    }
}
