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
package com.protonvpn.android.ui.drawer

import android.content.Intent
import android.icu.text.NumberFormat
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivityAlwaysOnBinding
import com.protonvpn.android.databinding.AlwaysOnStepBinding
import com.protonvpn.android.utils.HtmlTools

@ContentLayout(R.layout.activity_always_on)
@RequiresApi(24)
class AlwaysOnSettingsActivity : BaseActivityV2<ActivityAlwaysOnBinding, ViewModel>() {

    private val numberFormat = NumberFormat.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbarWithUpEnabled(binding.appbar.toolbar)

        with(binding.content) {
            step1.init(1, HtmlTools.fromHtml(getString(R.string.settingsAlwaysOnWindowStep1)))

            val textStep2 = SpannableString(getString(R.string.settingsAlwaysOnWindowStep2)).apply {
                insertDrawable("%1\$s", R.drawable.ic_cog_wheel_24, step2.text.textSize * 1.5f)
            }
            step2.init(2, textStep2)

            step3.init(3, HtmlTools.fromHtml(getString(R.string.settingsAlwaysOnWindowStep3)))

            step4.init(4, HtmlTools.fromHtml(getString(R.string.settingsAlwaysOnWindowStep4)))

            buttonOpenVpnSettings.setOnClickListener {
                startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
            }

            val learnMoreText = SpannableString(HtmlTools.fromHtml(getString(
                    R.string.settingsAlwaysOnWindowLearnMore)))
            learnMoreText.getSpans(0, learnMoreText.length, URLSpan::class.java).forEach { urlSpan ->
                makeClickable(learnMoreText, urlSpan) {
                    MaterialDialog.Builder(this@AlwaysOnSettingsActivity).theme(Theme.DARK)
                            .content(R.string.settingsAlwaysOnWindowLearnMoreText)
                            .positiveText(R.string.close)
                            .show()
                }
            }
            learnMore.text = learnMoreText
            learnMore.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun SpannableString.insertDrawable(placeholder: String, @DrawableRes drawableRes: Int, sizePx: Float) {
        val start = indexOf(placeholder)
        val drawable = getDrawable(drawableRes)!!.mutate().apply {
            setBounds(0, 0, sizePx.toInt(), sizePx.toInt())
        }
        setSpan(ImageSpan(drawable), start, start + placeholder.length, 0)
    }

    private fun AlwaysOnStepBinding.init(i: Int, string: CharSequence) {
        number.text = numberFormat.format(i)
        text.text = string
    }

    private fun makeClickable(text: SpannableString, span: URLSpan, clickFun: () -> Unit) {
        text.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) = clickFun()
        }, text.getSpanStart(span), text.getSpanEnd(span), text.getSpanFlags(span))
        text.removeSpan(span)
    }

    override fun initViewModel() {}
}
