/*
 * Copyright (c) 2025. Proton AG
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
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.SimpleTopAppBar
import com.protonvpn.android.base.ui.TopAppBarCloseIcon
import com.protonvpn.android.base.ui.copy
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.base.ui.theme.enableEdgeToEdgeVpn
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.FragmentPaymentPanelBinding
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.ui.planupgrade.UpgradeActivityHelper
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel
import com.protonvpn.android.utils.isNightMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PromoOfferIapActivity : BaseActivityV2() {

    private val viewModel by viewModels<PromoOfferIapViewModel>()
    // This screen is an activity because PaymentPanelFragment expects an UpgradeDialogViewModel in its parent activity.
    private val upgradeViewModel by viewModels<UpgradeDialogViewModel>()
    private val upgradeActivityHelper = UpgradeActivityHelper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeVpn()
        super.onCreate(savedInstanceState)
        val bgImageUrl = mutableStateOf<String?>(null)
        val bgImageContentDescription = mutableStateOf<String?>(null)
        setContent {
            VpnTheme {
                PromoOfferIap(
                    bgImageUrl = bgImageUrl.value,
                    bgImageContentDescription = bgImageContentDescription.value,
                    onClose = ::finish
                )
            }
        }

        lifecycleScope.launch {
            val notificationId = getNotificationId(intent)
            val offer = notificationId?.let { viewModel.getOfferViewState(it) }
            if (offer == null) {
                Toast.makeText(this@PromoOfferIapActivity, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
                ProtonLogger.logCustom(LogLevel.ERROR, LogCategory.UI, "No notification with ID $notificationId")
                finish()
            } else {
                bgImageUrl.value =
                    if (resources.configuration.isNightMode()) offer.imageUrlDark else offer.imageUrlLight
                bgImageContentDescription.value = offer.imageContentDescription
                upgradeViewModel.loadPlans(
                    planNames = listOf(offer.iapParams.planName),
                    cycles = listOf(offer.iapParams.cycle),
                    buttonLabelOverride = offer.buttonLabel,
                    showDiscountBadge = offer.iapParams.showDiscountBadge
                )
                upgradeViewModel.reportUpgradeFlowStart(UpgradeSource.PROMO_OFFER, offer.notificationReference)
            }
        }
        upgradeActivityHelper.onCreate(upgradeViewModel)
    }

    companion object {
        private const val EXTRA_NOTIFICATION_ID = "id"

        private fun getNotificationId(intent: Intent): String? =
            intent.getStringExtra(EXTRA_NOTIFICATION_ID)

        fun launch(context: Context, notificationId: String) {
            val intent = Intent(context, PromoOfferIapActivity::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            context.startActivity(intent)
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PromoOfferIap(
    bgImageUrl: String?,
    bgImageContentDescription: String?,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            SimpleTopAppBar(
                title = {},
                navigationIcon = { TopAppBarCloseIcon(onClose) },
                backgroundColor = Color.Transparent,
            )
        },
        bottomBar = {
            PaymentPanelBottomBar(modifier = Modifier
                .navigationBarsPadding()
                .largeScreenContentPadding()
            )
        },
    ) { paddingValues ->
        if (bgImageUrl != null) {
            GlideImage(
                bgImageUrl,
                contentDescription = bgImageContentDescription,
                contentScale = ContentScale.Inside,
                modifier = Modifier
                    .fillMaxSize()
                    .largeScreenContentPadding()
                    .padding(paddingValues.copy(top = 0.dp))
                    .verticalScroll(rememberScrollState())
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            )
        }
    }
}

@Composable
private fun PaymentPanelBottomBar(modifier: Modifier = Modifier) {
    AndroidViewBinding(FragmentPaymentPanelBinding::inflate, modifier = modifier)
}
