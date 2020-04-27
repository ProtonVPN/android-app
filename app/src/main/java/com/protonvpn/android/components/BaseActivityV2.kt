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
package com.protonvpn.android.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModel
import com.protonvpn.android.R
import dagger.android.support.DaggerAppCompatActivity

abstract class BaseActivityV2<DB : ViewDataBinding, VM : ViewModel> : DaggerAppCompatActivity() {

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    lateinit var binding: DB

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    lateinit var viewModel: VM

    protected abstract fun initViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.inflate(layoutInflater,
                AnnotationParser.getAnnotatedLayout(this), null, false)
        setContentView(binding.root)
        initViewModel()
    }

    fun initToolbarWithUpEnabled(toolbar: Toolbar) {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    fun openUrl(url: String?) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.openUrlError, url), Toast.LENGTH_LONG).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
