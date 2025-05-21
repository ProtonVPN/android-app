/*
 * Copyright (c) 2019 Proton AG
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
package com.protonvpn.android.ui.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityLicensesBinding
import com.protonvpn.android.utils.getThemeColor
import com.protonvpn.android.utils.isNightMode
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.compose.R as CoreR

@AndroidEntryPoint
class OssLicensesActivity : BaseActivityV2() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLicensesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initToolbarWithUpEnabled(binding.appbar.toolbar)
        initWebView(binding.content.webView)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView(webView: WebView) = with(webView) {
        setBackgroundColor(getThemeColor(CoreR.attr.proton_background_norm))
        settings.javaScriptEnabled = true
        val htmlFileSuffix = if (resources.configuration.isNightMode()) "dark" else "light"
        loadUrl("file:///android_asset/oss_licenses/oss_licenses_${htmlFileSuffix}.html")
    }
}
