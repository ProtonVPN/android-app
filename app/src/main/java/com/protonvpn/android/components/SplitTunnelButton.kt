/*
 * Copyright (c) 2018 Proton Technologies AG
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

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.protonvpn.android.R
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.splittunneling.AppsDialog
import com.protonvpn.android.ui.splittunneling.IpDialog

class SplitTunnelButton : FrameLayout {

    @BindView(R.id.textTitle) lateinit var textTitle: AppCompatTextView
    @BindView(R.id.textDescription) lateinit var textDescription: AppCompatTextView
    @BindView(R.id.buttonManage) lateinit var buttonManage: Button
    private var title: String? = null
    private var shouldUseApps: Boolean = false

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initAttrs(attrs)
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initAttrs(attrs)
        init()
    }

    private fun initAttrs(attrs: AttributeSet) {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.SplitTunnelButton, 0, 0)

        try {
            title = a.getString(R.styleable.SplitTunnelButton_title)
            shouldUseApps = a.getBoolean(R.styleable.SplitTunnelButton_shouldHandleApps, false)
        } finally {
            a.recycle()
        }
    }

    private fun init() {
        View.inflate(context, R.layout.item_splittunel_setting, this)
        ButterKnife.bind(this)
        textTitle.text = title
    }

    @SuppressLint("CheckResult")
    fun initTextUpdater(lifecycleOwner: LifecycleOwner, userData: UserData) {
        initText(userData)
        userData.updateEvent.observe(lifecycleOwner) {
            initText(userData)
        }
    }

    private fun initText(userData: UserData) {
        textDescription.text = if (shouldUseApps)
            context.getString(R.string.settingsExcludeAppsInfo, userData.splitTunnelApps.size)
        else
            context.getString(R.string.settingsExcludeIPAddressesInfo, userData.splitTunnelIpAddresses.size)
    }

    @OnClick(R.id.buttonManage)
    fun buttonManage() {
        val fm = (context as FragmentActivity).supportFragmentManager
        if (shouldUseApps) {
            val appsDialog = AppsDialog()
            appsDialog.show(fm, "fragment_split_tunnel")
        } else {
            val ipDialog = IpDialog()
            ipDialog.show(fm, "fragment_split_tunnel")
        }
    }
}
