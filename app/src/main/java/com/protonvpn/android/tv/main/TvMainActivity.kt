/*
 * Copyright (c) 2020 Proton AG
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
package com.protonvpn.android.tv.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.commit
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.databinding.ActivityTvMainBinding
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.android.ui.main.AccountViewModel
import com.protonvpn.android.ui.main.MainActivityHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TvMainActivity : BaseTvActivity() {

    private val accountViewModel: AccountViewModel by viewModels()

    @Inject
    lateinit var isTv: IsTvCheck

    private val helper = object : MainActivityHelper(this) {

        override suspend fun onLoginNeeded() {
            clearMainFragment()
            loginLauncher.launch(Unit)
        }

        override suspend fun onReady() = with(supportFragmentManager) {
            if (findFragmentById(R.id.container) == null)
                commit {
                    add(R.id.container, TvMainFragment::class.java, null)
                }
        }
    }

    private val loginLauncher = registerForActivityResult(TvLoginActivity.createContract()) {
        if (it.resultCode == Activity.RESULT_CANCELED)
            finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityTvMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        helper.onCreate(accountViewModel)

        val isTvIntent = intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        isTv.onUiLaunched(isTvIntent)
    }

    private fun clearMainFragment() = with(supportFragmentManager) {
        commit {
            findFragmentById(R.id.container)?.let {
                remove(it)
            }
        }
    }
}
