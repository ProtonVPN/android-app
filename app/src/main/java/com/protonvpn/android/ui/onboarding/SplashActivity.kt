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
package com.protonvpn.android.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.protonvpn.android.redesign.app.ui.MainActivity
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.tv.main.TvMainActivity
import com.protonvpn.android.ui.deeplinks.DeepLinkHandler
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.notification.presentation.deeplink.HandleDeeplinkIntent
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var isTv: IsTvCheck

    @Inject
    lateinit var deepLinkHandler: DeepLinkHandler

    @Inject
    lateinit var handleDeeplinkIntent: HandleDeeplinkIntent

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { true }
        super.onCreate(savedInstanceState)

        processDeepLink()

        val isTvIntent = intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        isTv.onUiLaunched(isTvIntent)

        val nextActivity = if (isTv()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            TvMainActivity::class.java
        } else {
            MainActivity::class.java
        }

        startActivity(
            Intent(this, nextActivity).apply {
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        )

        handleDeeplinkIntent(intent)

        // Remove the task to make sure the main activity has its own. See VPNAND-763.
        finishAndRemoveTask()
        overridePendingTransition(0, 0) // Disable exit animation.
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleDeeplinkIntent(it) }
    }

    private fun processDeepLink() {
        val intentUri = intent.data
        if (intent.action == Intent.ACTION_VIEW && intentUri != null) {
            deepLinkHandler.processDeepLink(intentUri)
        }
    }
}
