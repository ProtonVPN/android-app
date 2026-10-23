/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.tv.upsell

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.tv.buttons.TvTextButton
import com.protonvpn.android.tv.ui.tvGradientGlowBackground
import com.protonvpn.android.ui.planupgrade.comparison_table.BenefitTableRowDefaults
import com.protonvpn.android.ui.planupgrade.comparison_table.SparklingFlag
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.CountryTools
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.utils.currentLocale
import java.util.Locale

@Composable
fun TvUpsellLayoutQrCode(
    content: TvUpsellContent,
    freeCountries: Int,
    plusCountriesRounded: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (content) {
        is TvUpsellContent.Country ->
            TvUpsellQrCodeCountry(content.countryId, freeCountries, plusCountriesRounded, modifier)
        TvUpsellContent.CustomDns,
        TvUpsellContent.LanConnections -> TvUpsellQrCodeCustomization(modifier)
        TvUpsellContent.NetShield -> TvUpsellQrCodeNetShield(modifier)
        TvUpsellContent.SplitTunneling -> TvUpsellQrCodeSplitTunneling(modifier)
        TvUpsellContent.SubscriptionExpired -> TvUpsellQrCodeDowngrade(
            title = stringResource(R.string.upsell_tv_subscription_expired_title),
            description = stringResource(R.string.upsell_tv_non_iap_subscription_expired_description),
            qrCaption = stringResource(R.string.upsell_tv_subscription_expired_description),
            qrCode = R.drawable.tv_upsell_qr_code,
            qrUrl = Constants.URL_TV_SUBSCRIBE,
            onClose = onClose,
            modifier = modifier,
        )
        TvUpsellContent.PaymentPending -> TvUpsellQrCodeDowngrade(
            title = stringResource(R.string.notification_delinquent_title),
            description = stringResource(R.string.upsell_tv_non_iap_payment_pending_description),
            qrCaption = stringResource(R.string.notification_delinquent_content),
            qrCode = R.drawable.tv_upsell_delinquent_qr_code,
            qrUrl = Constants.URL_TV_DELINQUENT_SUBSCRIBE,
            onClose = onClose,
            modifier = modifier,
        )
    }
}

@Composable
private fun TvUpsellQrCodeCountry(
    countryId: CountryId?,
    freeCountries: Int,
    plusCountriesRounded: Int,
    modifier: Modifier = Modifier,
) {
    val title = if (countryId != null) {
        // Note: CountryId.label() can't be used, LocalLocale is not provided on TV.
        val locale = if (LocalInspectionMode.current) {
            LocalConfiguration.current.locale ?: Locale.US
        } else {
            LocalConfiguration.current.currentLocale()
        }
        stringResource(R.string.upsell_panel_country_title, CountryTools.getFullName(locale, countryId.countryCode))
    } else {
        stringResource(R.string.upsell_panel_countries_title)
    }
    TvUpsellQrCodeLayout(
        image = {
            if (countryId != null) {
                SparklingFlag(countryId)
            } else {
                Image(painterResource(R.drawable.upsell_header_countries), contentDescription = null)
            }
        },
        title = title,
        footnote = if (countryId != null) stringResource(R.string.upsell_panel_streaming_footer) else null,
        modifier = modifier,
    ) {
        BenefitTableFreePlusHeaderTv()
        BenefitTableRowTv(
            stringResource(R.string.upsell_panel_general_benefit_countries),
            "%d".format(freeCountries),
            stringResource(R.string.upsell_panel_general_benefit_countries_rounded, plusCountriesRounded),
        )
        BenefitTableRowNoYesTv(
            stringResource(
                if (countryId != null) {
                    R.string.upsell_panel_streaming_benefit_platforms
                } else {
                    R.string.upsell_tv_non_iap_country_benefit_speed
                }
            )
        )
        BenefitTableRowNoYesTv(
            stringResource(R.string.upsell_panel_general_benefit_shows),
            secondPlanBackgroundShape = BenefitTableRowDefaults.ShapeBottom,
            bottomSeparator = false,
        )
    }
}

@Composable
private fun TvUpsellQrCodeCustomization(
    modifier: Modifier = Modifier,
) {
    TvUpsellQrCodeLayout(
        image = R.drawable.upsell_header_advanced_customization,
        title = stringResource(R.string.upsell_panel_adv_customization_title),
        description = AnnotatedString(stringResource(R.string.upsell_tv_non_iap_customization_description)),
        modifier = modifier,
    ) {
        BenefitTableFreePlusHeaderTv()
        BenefitTableRowNoYesTv(stringResource(R.string.upsell_tv_non_iap_benefit_lan))
        BenefitTableRowNoYesTv(stringResource(R.string.upsell_panel_adv_customization_benefit_dns))
        BenefitTableRowTv(
            stringResource(R.string.upsell_panel_general_benefit_speed),
            stringResource(R.string.upsell_panel_general_benefit_speed_standard),
            stringResource(R.string.upsell_panel_general_benefit_speed_highest),
            bottomSeparator = false,
            secondPlanBackgroundShape = BenefitTableRowDefaults.ShapeBottom,
        )
    }
}

@Composable
private fun TvUpsellQrCodeNetShield(
    modifier: Modifier = Modifier,
) {
    TvUpsellQrCodeLayout(
        image = R.drawable.tv_upsell_header_netshield,
        title = stringResource(R.string.upsell_tv_non_iap_netshield_title),
        description = AnnotatedString.fromHtml(stringResource(R.string.upsell_tv_netshield_description)),
        modifier = modifier,
    ) {
        BenefitTableFreePlusHeaderTv()
        BenefitTableRowNoYesTv(stringResource(R.string.upsell_panel_netshield_benefit_malware))
        BenefitTableRowNoYesTv(stringResource(R.string.upsell_panel_netshield_benefit_trackers))
        BenefitTableRowNoYesTv(
            stringResource(R.string.upsell_tv_non_iap_netshield_benefit_ads),
            secondPlanBackgroundShape = BenefitTableRowDefaults.ShapeBottom,
            bottomSeparator = false,
        )
    }
}

@Composable
private fun TvUpsellQrCodeSplitTunneling(
    modifier: Modifier = Modifier,
) {
    TvUpsellQrCodeLayout(
        image = R.drawable.upsell_header_split_tunneling,
        title = stringResource(R.string.upsell_panel_split_tunneling_title),
        description = AnnotatedString.fromHtml(stringResource(R.string.upsell_tv_split_tunneling_description)),
        modifier = modifier,
    ) {
        BenefitTableFreePlusHeaderTv()
        BenefitTableRowNoYesTv(stringResource(R.string.upsell_panel_split_tunneling_benefit_split))
        BenefitTableRowTv(
            stringResource(R.string.upsell_panel_general_benefit_devices),
            "%d".format(1),
            "%d".format(Constants.MAX_CONNECTIONS_IN_PLUS_PLAN),
        )
        BenefitTableRowNoYesTv(
            stringResource(R.string.upsell_tv_non_iap_benefit_lan),
            secondPlanBackgroundShape = BenefitTableRowDefaults.ShapeBottom,
            bottomSeparator = false,
        )
    }
}

@Composable
private fun TvUpsellQrCodeDowngrade(
    title: String,
    description: String,
    qrCaption: String,
    @DrawableRes qrCode: Int,
    qrUrl: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    TvUpsellTwoPanelLayout(
        caption = qrCaption,
        qrCode = qrCode,
        qrUrl = qrUrl,
        modifier = modifier,
    ) {
        Image(painterResource(R.drawable.upsell_tv_subscription_expired), contentDescription = null)
        Text(
            text = title,
            style = ProtonTheme.typography.hero,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = ProtonTheme.typography.body1Regular,
            color = ProtonTheme.colors.textWeak,
            textAlign = TextAlign.Center,
        )
        TvTextButton(
            text = stringResource(R.string.upsell_tv_non_iap_maybe_later),
            textStyle = ProtonTheme.typography.body1Medium,
            onClick = onClose,
            modifier = Modifier.focusRequester(focusRequester),
        )
    }
}

@Composable
private fun TvUpsellQrCodeLayout(
    @DrawableRes image: Int,
    title: String,
    modifier: Modifier = Modifier,
    description: AnnotatedString? = null,
    footnote: String? = null,
    benefitTable: @Composable () -> Unit,
) {
    TvUpsellQrCodeLayout(
        image = { Image(painterResource(image), contentDescription = null) },
        title = title,
        modifier = modifier,
        description = description,
        footnote = footnote,
        benefitTable = benefitTable,
    )
}

@Composable
private fun TvUpsellQrCodeLayout(
    image: @Composable () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    description: AnnotatedString? = null,
    footnote: String? = null,
    benefitTable: @Composable () -> Unit,
) {
    TvUpsellTwoPanelLayout(
        caption = stringResource(R.string.upsell_tv_non_iap_scan_code),
        modifier = modifier,
    ) {
        image()
        Text(
            text = title,
            style = ProtonTheme.typography.hero,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Text(
                text = description,
                style = ProtonTheme.typography.body1Regular,
                color = ProtonTheme.colors.textWeak,
                textAlign = TextAlign.Center,
            )
        }
        Column(
            modifier = Modifier.width(360.dp)
        ) {
            benefitTable()
        }
        if (footnote != null) {
            Text(
                text = footnote,
                style = ProtonTheme.typography.captionRegular,
                color = ProtonTheme.colors.textWeak,
                modifier = Modifier.width(360.dp)
            )
        }
    }
}

// Content on the left, QR code on the right.
@Composable
private fun TvUpsellTwoPanelLayout(
    caption: String,
    modifier: Modifier = Modifier,
    @DrawableRes qrCode: Int = R.drawable.tv_upsell_qr_code,
    qrUrl: String = Constants.URL_TV_SUBSCRIBE,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .tvGradientGlowBackground()
            .padding(horizontal = 72.dp, vertical = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
        QrCodePanel(caption, qrCode, qrUrl)
    }
}

@Composable
private fun QrCodePanel(
    caption: String,
    @DrawableRes qrCode: Int,
    qrUrl: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(260.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Image(
            painter = painterResource(qrCode),
            contentDescription = null,
            modifier = Modifier
                .size(216.dp)
                .background(Color.White, ProtonTheme.shapes.large)
                .padding(21.dp)
        )
        Text(
            text = caption,
            style = ProtonTheme.typography.body1Regular,
            color = ProtonTheme.colors.textWeak,
            textAlign = TextAlign.Center,
        )
        Text(
            text = qrUrl,
            style = ProtonTheme.typography.headline,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TvUpsellLayoutQrCodePreview(content: TvUpsellContent) {
    ProtonVpnTvPreview {
        TvUpsellLayoutQrCode(content, 10, 120, onClose = {}, modifier = Modifier.fillMaxSize())
    }
}

@ProtonVpnTvPreview
@Composable
private fun TvUpsellLayoutQrCodeCountryPreview() =
    TvUpsellLayoutQrCodePreview(TvUpsellContent.Country(CountryId("US")))

@ProtonVpnTvPreview
@Composable
private fun TvUpsellLayoutQrCodeNoCountryPreview() =
    TvUpsellLayoutQrCodePreview(TvUpsellContent.Country(null))

@ProtonVpnTvPreview
@Composable
private fun TvUpsellLayoutQrCodeNetShieldPreview() =
    TvUpsellLayoutQrCodePreview(TvUpsellContent.NetShield)

@ProtonVpnTvPreview
@Composable
private fun TvUpsellLayoutQrCodeCustomizationPreview() =
    TvUpsellLayoutQrCodePreview(TvUpsellContent.CustomDns)

@ProtonVpnTvPreview
@Composable
private fun TvUpsellLayoutQrCodeSplitTunnelingPreview() =
    TvUpsellLayoutQrCodePreview(TvUpsellContent.SplitTunneling)

@ProtonVpnTvPreview
@Composable
private fun TvUpsellLayoutQrCodeSubscriptionExpiredPreview() =
    TvUpsellLayoutQrCodePreview(TvUpsellContent.SubscriptionExpired)

@ProtonVpnTvPreview
@Composable
private fun TvUpsellLayoutQrCodePaymentPendingPreview() =
    TvUpsellLayoutQrCodePreview(TvUpsellContent.PaymentPending)
