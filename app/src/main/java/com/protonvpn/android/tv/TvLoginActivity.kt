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
package com.protonvpn.android.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.databinding.ActivityTvLoginBinding
import com.protonvpn.android.tv.login.TvLoginViewModel
import com.protonvpn.android.tv.login.TvLoginViewState
import com.protonvpn.android.tv.main.TvMainActivity
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.ViewUtils.initLolipopButtonFocus
import com.protonvpn.android.utils.ViewUtils.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.proton.core.presentation.utils.currentLocale
import me.proton.core.presentation.utils.openBrowserLink
import me.proton.core.util.kotlin.exhaustive
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class TvLoginActivity : BaseTvActivity() {

    private val binding by viewBinding(ActivityTvLoginBinding::inflate)
    val viewModel by viewModels<TvLoginViewModel>()

    private val timeLeftFormatter by lazy { SimpleDateFormat("m:ss", resources.configuration.currentLocale()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        lifecycleScope.launch {
            viewModel.onEnterScreen(lifecycleScope)
        }
        with(binding) {
            actionButton.initLolipopButtonFocus()
            actionButton.setOnClickListener {
                viewModel.startLogin(lifecycleScope)
            }

            val numberFormat = NumberFormat.getInstance()
            stepNumber1.text = numberFormat.format(1)
            stepNumber2.text = numberFormat.format(2)
            stepNumber3.text = numberFormat.format(3)

            step1Description.text = HtmlTools.fromHtml(
                getString(R.string.tv_login_step1_description, Constants.TV_LOGIN_URL))

            createAccountDescription.text =
                HtmlTools.fromHtml(getString(R.string.tv_login_welcome_description_bottom, Constants.TV_SIGNUP_LINK))

            if (viewModel.displayStreamingIcons) {
                with(streamIcons) {
                    streamingNetflix.addStreamingView("Netflix", R.drawable.ic_streaming_netflix)
                    streamingDisney.addStreamingView("Disney+", R.drawable.ic_streaming_disney)
                    streamingPrime.addStreamingView("Prime", R.drawable.ic_streaming_prime)
                }
            }
        }

        viewModel.state.observe(this, Observer { updateState(it) })
    }

    private fun TextView.init(@StringRes contentRes: Int = 0, content: String? = null) {
        isVisible = content != null || contentRes != 0
        if (content != null)
            text = content
        else if (contentRes != 0)
            setText(contentRes)
    }

    private fun TextView.initLink(url: String? = null) {
        isVisible = url != null
        if (url != null) {
            text = url
            setOnClickListener {
                openBrowserLink(url)
            }
        }
    }

    private fun updateState(state: TvLoginViewState) = with(binding) {
        loginWaitContainer.isVisible = state is TvLoginViewState.PollingSession
        timerContainer.isVisible = state is TvLoginViewState.PollingSession
        streamIcons.container.isVisible = viewModel.displayStreamingIcons && state is TvLoginViewState.Welcome
        title.init(state.titleRes, state.title)
        helpLink.initLink(state.helpLink)
        description.init(state.descriptionRes)
        description2.init(state.description2Res)
        actionButton.init(state.buttonLabelRes)
        loadingView.isVisible = state == TvLoginViewState.Loading || state == TvLoginViewState.Success
        createAccountDescription.isVisible = state == TvLoginViewState.Welcome
        when (state) {
            TvLoginViewState.Welcome, TvLoginViewState.FetchingCode -> {}
            is TvLoginViewState.PollingSession -> {
                timer.text = timeLeftFormatter.format(Date(TimeUnit.SECONDS.toMillis(state.secondsLeft)))
                updateCode(state.code)
            }
            is TvLoginViewState.Error -> {
                loadingView.cancelAnimation()
            }
            is TvLoginViewState.Loading -> {
                startLoadingAnimation()
            }
            is TvLoginViewState.Success -> {
                setResult(Activity.RESULT_OK)
                if (loadingView.isAnimating)
                    finishLoadingAnimation()
                finishLogin()
            }
            TvLoginViewState.ConnectionAllocationPrompt -> {}
        }.exhaustive
        // Focus the action button first, not the link.
        if (actionButton.isVisible) actionButton.requestFocus()
    }

    private fun updateCode(code: String) = with(binding.codeContainer) {
        var i = 0
        children.forEach {
            if (it.id != R.id.codeSeparator)
                (it as TextView).text = code[i++].toString()
        }
    }

    private fun startLoadingAnimation() = with(binding.loadingView) {
        isVisible = true
    }

    private fun finishLoadingAnimation() = with(binding.loadingView) {
        isVisible = false
    }

    private fun finishLogin() {
        startActivity(Intent(this, TvMainActivity::class.java))
        finish()
    }

    companion object {
        fun createContract() = object : ActivityResultContract<Unit, ActivityResult>() {
            override fun createIntent(context: Context, input: Unit) =
                Intent(context, TvLoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            override fun parseResult(resultCode: Int, intent: Intent?) = ActivityResult(resultCode, null)
        }
    }
}
