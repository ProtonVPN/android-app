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

package com.protonvpn.android.auth.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.protonvpn.android.R
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.auth.presentation.ui.signup.TermsConditionsDialogFragment
import me.proton.core.presentation.utils.inTransaction

@AndroidEntryPoint
class TermsAndConditionsActivity : FragmentActivity(R.layout.activity_terms_and_conditions) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.registerFragmentLifecycleCallbacks(finishWhenNoFragmentInContainer, true)
        if (savedInstanceState == null) {
            supportFragmentManager.inTransaction {
                replace(R.id.fragment_container, TermsConditionsDialogFragment())
            }
        }
    }

    override fun onDestroy() {
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(finishWhenNoFragmentInContainer)
        super.onDestroy()
    }

    private val finishWhenNoFragmentInContainer = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
            window.decorView.post {
                if (isFinishing) return@post
                if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
                    finish()
                }
            }
        }
    }
}