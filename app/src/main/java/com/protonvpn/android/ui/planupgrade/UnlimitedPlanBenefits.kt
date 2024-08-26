/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.ui.planupgrade

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.PagerIndicatorDots
import com.protonvpn.android.base.ui.SimpleModalBottomSheet
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import com.protonvpn.android.utils.Constants
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

sealed interface PlanBenefitText {
    class String(@StringRes val resId: Int, val argument: Int? = null) : PlanBenefitText
    class Plural(@PluralsRes val resId: Int, val count: Int) : PlanBenefitText
}

data class AppBenefits(
    @DrawableRes val logo: Int,
    @StringRes val logoContentDescription: Int,
    @ColorInt val backgroundGradient: Triple<Int, Int, Int>,
    val mainBenefits: List<PlanBenefitText>,
    val allBenefits: List<PlanBenefit>
)

data class PlanBenefit(
    @DrawableRes val iconRes: Int,
    val text: PlanBenefitText
)

@Composable
fun UnlimitedPlanBenefitsBottomSheet(
    onDismissRequest: () -> Unit,
    focusedApp: AppBenefits,
    modifier: Modifier = Modifier,
) {
    SimpleModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier) {
        val apps = UnlimitedPlanBenefits.apps
        AppBenefitsPager(apps = apps, initialPageIndex = apps.indexOf(focusedApp).coerceAtLeast(0))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppBenefitsPager(apps: List<AppBenefits>, initialPageIndex: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        val pagerState = rememberPagerState(initialPageIndex, pageCount = { apps.size })
        HorizontalPager(
            state = pagerState,
            beyondBoundsPageCount = apps.size, // Render all to set height to tallest item.
            verticalAlignment = Alignment.Top,
        ) { pageIndex ->
            AppBenefitsPage(
                apps[pageIndex],
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
        PagerIndicatorDots(pagerState,
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp))
    }
}

@Composable
private fun AppBenefitsPage(app: AppBenefits, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Image(
            painter = painterResource(id = app.logo),
            contentDescription = stringResource(app.logoContentDescription),
            modifier = Modifier
                .padding(vertical = 16.dp)
                .height(36.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .border(1.dp, ProtonTheme.colors.separatorNorm, ProtonTheme.shapes.large)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            app.allBenefits.forEach { benefit ->
                Row {
                    Icon(
                        painterResource(benefit.iconRes),
                        contentDescription = null,
                        tint = ProtonTheme.colors.iconAccent,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(20.dp),
                    )
                    Text(planStringResource(benefit.text))
                }
            }
        }
    }
}

@Composable
fun planStringResource(text: PlanBenefitText): String = when (text) {
    is PlanBenefitText.String ->
        if (text.argument != null) stringResource(text.resId, text.argument) else stringResource(text.resId)

    is PlanBenefitText.Plural -> pluralStringResource(text.resId, count = text.count, text.count)
}


@Preview
@Composable
private fun PreviewAppBenefitsPager() {
    LightAndDarkPreview {
        AppBenefitsPager(UnlimitedPlanBenefits.apps, initialPageIndex = 1)
    }
}

object UnlimitedPlanBenefits {
    val defaultGradient = singleColorGradient(0xFF8563CE.toInt())

    val apps = listOf(
        AppBenefits(
            CoreR.drawable.logo_vpn_with_text,
            R.string.app_name,
            singleColorGradient(0xFF2E737B.toInt()),
            mainBenefits = listOf(
                PlanBenefitText.String(R.string.upgrade_unlimited_vpn_unlimited_countries),
                PlanBenefitText.String(R.string.upgrade_unlimited_vpn_all_features)
            ),
            allBenefits = listOf(
                PlanBenefit(
                    CoreR.drawable.ic_proton_globe,
                    PlanBenefitText.String(R.string.upgrade_unlimited_vpn_any_location)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_bolt,
                    PlanBenefitText.String(R.string.upgrade_unlimited_vpn_higher_speeds)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_mobile,
                    PlanBenefitText.Plural(R.plurals.upgrade_unlimited_vpn_many_devices, Constants.UNLIMITED_PLAN_VPN_CONNECTIONS)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_play,
                    PlanBenefitText.String(R.string.upgrade_unlimited_vpn_streaming)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_circle_slash,
                    PlanBenefitText.String(R.string.upgrade_unlimited_vpn_adblocker)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_locks,
                    PlanBenefitText.String(R.string.upgrade_unlimited_vpn_double_vpn)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_arrow_right_arrow_left,
                    PlanBenefitText.String(R.string.upgrade_unlimited_vpn_p2p)
                )
            )
        ),
        AppBenefits(
            CoreR.drawable.logo_mail_with_text,
            R.string.mail_app_name,
            singleColorGradient(0xFF473594.toInt()),
            mainBenefits = listOf(
                PlanBenefitText.Plural(R.plurals.upgrade_unlimited_mail_custom_domains, Constants.UNLIMITED_PLAN_MAIL_DOMAINS),
                PlanBenefitText.Plural(R.plurals.upgrade_unlimited_mail_attachments, Constants.UNLIMITED_PLAN_MAIL_ATTACHMENT_MBS)
            ),
            allBenefits = listOf(
                PlanBenefit(
                    CoreR.drawable.ic_proton_mailbox,
                    PlanBenefitText.Plural(R.plurals.upgrade_unlimited_mail_many_addresses, Constants.UNLIMITED_PLAN_MAIL_ADDRESSES)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_folder_plus,
                    PlanBenefitText.String(R.string.upgrade_unlimited_mail_unlimited_folders)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_at,
                    PlanBenefitText.Plural(R.plurals.upgrade_unlimited_mail_custom_domains, Constants.UNLIMITED_PLAN_MAIL_DOMAINS)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_shield_2_bolt,
                    PlanBenefitText.String(R.string.upgrade_unlimited_mail_dark_web)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_alias,
                    PlanBenefitText.String(R.string.upgrade_unlimited_mail_aliases)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_reply,
                    PlanBenefitText.String(R.string.upgrade_unlimited_mail_autoreply)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_clock_paper_plane,
                    PlanBenefitText.String(R.string.upgrade_unlimited_mail_scheduled_send)
                )
            )
        ),
        AppBenefits(
            CoreR.drawable.logo_calendar_with_text,
            R.string.calendar_app_name,
            singleColorGradient(0xFF3A51A6.toInt()),
            mainBenefits = listOf(
                PlanBenefitText.Plural(R.plurals.upgrade_unlimited_calendar_calendars, Constants.UNLIMITED_PLAN_CALENDARS),
                PlanBenefitText.String(R.string.upgrade_unlimited_calendar_sharing)
            ),
            allBenefits = listOf(
                PlanBenefit(
                    CoreR.drawable.ic_proton_calendar_today,
                    PlanBenefitText.Plural(R.plurals.upgrade_unlimited_calendar_calendars, Constants.UNLIMITED_PLAN_CALENDARS)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_paper_plane,
                    PlanBenefitText.String(R.string.upgrade_unlimited_calendar_sharing)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_users_plus,
                    PlanBenefitText.String(R.string.upgrade_unlimited_calendar_invites)
                )
            )
        ),
        AppBenefits(
            CoreR.drawable.logo_drive_with_text,
            R.string.drive_app_name,
            singleColorGradient(0xFF9C428C.toInt()),
            mainBenefits = listOf(
                PlanBenefitText.String(R.string.upgrade_unlimited_drive_encryption),
                PlanBenefitText.String(R.string.upgrade_unlimited_drive_storage_size, Constants.UNLIMITED_PLAN_DRIVE_STORAGE_GB)
            ),
            allBenefits = listOf(
                PlanBenefit(
                    CoreR.drawable.ic_proton_storage,
                    PlanBenefitText.String(R.string.upgrade_unlimited_drive_storage_size, Constants.UNLIMITED_PLAN_DRIVE_STORAGE_GB)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_link,
                    PlanBenefitText.String(R.string.upgrade_unlimited_drive_link_share)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_lock,
                    PlanBenefitText.String(R.string.upgrade_unlimited_drive_sharing_security)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_clock_rotate_left,
                    PlanBenefitText.String(R.string.upgrade_unlimited_drive_version_history)
                )
            )
        ),
        AppBenefits(
            CoreR.drawable.logo_pass_with_text,
            R.string.pass_app_name,
            Triple(0xFFA86B83.toInt(), 0x80B578D9.toInt(), 0x00B578D9.toInt()),
            mainBenefits = listOf(
                PlanBenefitText.String(R.string.upgrade_unlimited_pass_manager),
                PlanBenefitText.String(R.string.upgrade_unlimited_pass_logins_aliases)
            ),
            allBenefits = listOf(
                PlanBenefit(
                    CoreR.drawable.ic_proton_vault,
                    PlanBenefitText.Plural(R.plurals.upgrade_unlimited_pass_vaults, Constants.UNLIMITED_PLAN_PASS_VAULTS)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_pen,
                    PlanBenefitText.String(R.string.upgrade_unlimited_pass_logins)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_alias,
                    PlanBenefitText.String(R.string.upgrade_unlimited_pass_aliases)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_credit_card,
                    PlanBenefitText.String(R.string.upgrade_unlimited_pass_credit_cards)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_mobile,
                    PlanBenefitText.String(R.string.upgrade_unlimited_pass_devices)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_user_plus,
                    PlanBenefitText.Plural(R.plurals.upgrade_unlimited_pass_users, Constants.UNLIMITED_PLAN_PASS_USERS)
                ),
                PlanBenefit(
                    CoreR.drawable.ic_proton_key,
                    PlanBenefitText.String(R.string.upgrade_unlimited_pass_2fa_auth)
                ),
            )
        )
    )

    private fun singleColorGradient(@ColorInt color: Int) = Triple(
        ColorUtils.setAlphaComponent(color, 0xFF),
        ColorUtils.setAlphaComponent(color, 0x80),
        ColorUtils.setAlphaComponent(color, 0x00)
    )
}
