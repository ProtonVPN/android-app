/*
 * Copyright (c) 2021 Proton Technologies AG
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

package com.protonvpn.android.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.protonvpn.android.ui.home.HomeActivity
import com.protonvpn.android.ui.login.AssignVpnConnectionActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MobileMainActivity : AppCompatActivity() {

    private lateinit var mainLauncher: ActivityResultLauncher<Unit>
    private val accountViewModel: AccountViewModel by viewModels()
    private val helper = object : MainActivityHelper(this) {

        override suspend fun onLoginNeeded() {
            accountViewModel.startLogin()
        }

        override suspend fun onReady() {
            mainLauncher.launch(Unit)
        }

        override fun onAssignConnectionNeeded() {
            startActivity(Intent(this@MobileMainActivity, AssignVpnConnectionActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().setKeepOnScreenCondition(SplashScreen.KeepOnScreenCondition {
            true
        })
        helper.onCreate(accountViewModel)
        mainLauncher = registerForActivityResult(createHomeContract()) {
            if (it.resultCode == Activity.RESULT_CANCELED)
                finish()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        helper.onNewIntent(accountViewModel)
    }

    private fun createHomeContract() = object : ActivityResultContract<Unit, ActivityResult>() {
        override fun createIntent(context: Context, input: Unit) = Intent(context, HomeActivity::class.java)
        override fun parseResult(resultCode: Int, intent: Intent?) = ActivityResult(resultCode, null)
    }
}
