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
package com.protonvpn.android.ui.login

import android.content.Context
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.withStyledAttributes
import androidx.lifecycle.ViewModelProvider
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivityTroubleshootBinding
import com.protonvpn.android.databinding.ItemTroubleshootingInfoBinding
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.openProtonUrl
import javax.inject.Inject

@ContentLayout(R.layout.activity_troubleshoot)
class TroubleshootActivity : BaseActivityV2<ActivityTroubleshootBinding, TroubleshootViewModel>() {

    companion object {
        private const val TOR_URL = "https://www.torproject.org/"
        private const val PROTON_STATUS_URL = "https://protonstatus.com/"
        private const val SUPPORT_URL = "https://protonvpn.com/support-form"
        private const val MAIL_URL = "mailto:support@protonvpn.com"
        private const val TWITTER_URL = "https://twitter.com/ProtonVPN"
    }

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbarWithUpEnabled(binding.appbar.toolbar)

        with(binding.content) {
            switchDnsOverHttps.isChecked = viewModel.userData.apiUseDoH
            switchDnsOverHttps.setOnCheckedChangeListener { _, checked ->
                viewModel.dnsOverHttpsEnabled = checked
            }

            infoIspProblem.setDescription(HtmlTools.fromHtml(getString(
                    R.string.troubleshootIspProblemDescription, TOR_URL)))

            infoGovBlock.setDescription(HtmlTools.fromHtml(getString(
                    R.string.troubleshootGovernmentBlockDescription, TOR_URL)))

            infoProtonDown.setDescription(HtmlTools.fromHtml(getString(
                    R.string.troubleshootProtonDownDescription, PROTON_STATUS_URL)))

            textCustomerSupport.setOnClickListener {
                openProtonUrl(SUPPORT_URL)
            }
        }
    }

    override fun initViewModel() {
        viewModel = ViewModelProvider(this, viewModelFactory).get(TroubleshootViewModel::class.java)
    }
}

class TroubleshootInfoView : LinearLayout {

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    ) {
        init(context, attrs)
    }

    val binding = ItemTroubleshootingInfoBinding.inflate(LayoutInflater.from(context), this)

    private fun init(context: Context, attrs: AttributeSet?) {
        orientation = VERTICAL
        context.withStyledAttributes(attrs, R.styleable.TroubleshootingInfoView) {
            binding.textTitle.text = getString(R.styleable.TroubleshootingInfoView_title)
            binding.textDescription.text = getString(R.styleable.TroubleshootingInfoView_infoText)
        }
        binding.textDescription.movementMethod = LinkMovementMethod.getInstance()
    }

    fun setDescription(text: CharSequence) {
        binding.textDescription.text = text
    }
}
