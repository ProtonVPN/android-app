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
package com.protonvpn.android.tv

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieDrawable
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivityTvLoginBinding
import com.protonvpn.android.tv.login.TvLoginViewModel
import com.protonvpn.android.tv.login.TvLoginViewState
import com.protonvpn.android.tv.main.TvMainActivity
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.onAnimationEnd
import org.apache.commons.lang3.time.DurationFormatUtils
import java.text.NumberFormat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContentLayout(R.layout.activity_tv_login)
class TvLoginActivity : BaseTvActivity<ActivityTvLoginBinding>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    val viewModel by viewModels<TvLoginViewModel> { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.startLogin(lifecycleScope)
        with(binding) {
            retryButton.setOnClickListener {
                viewModel.startLogin(lifecycleScope)
            }

            val numberFormat = NumberFormat.getInstance()
            stepNumber1.text = numberFormat.format(1)
            stepNumber2.text = numberFormat.format(2)
            stepNumber3.text = numberFormat.format(3)

            step1Description.setText(HtmlTools.fromHtml(
                getString(R.string.tv_login_step1_description, Constants.TV_LOGIN_URL)))
        }

        viewModel.state.observe(this, Observer { updateState(it) })
    }

    private fun updateState(state: TvLoginViewState) = with(binding) {
        loginWaitContainer.isVisible = state is TvLoginViewState.PollingSession
        retryButton.isVisible = state is TvLoginViewState.Error
        loadingView.isVisible = state == TvLoginViewState.Loading || state == TvLoginViewState.Success
        when (state) {
            TvLoginViewState.FetchingCode -> {
                title.setText(R.string.tv_login_title_loading)
            }
            is TvLoginViewState.PollingSession -> {
                title.setText(R.string.tv_login_title_signin)
                timer.text = DurationFormatUtils.formatDuration(
                    TimeUnit.SECONDS.toMillis(state.secondsLeft), "m:ss")
                updateCode(state.code)
            }
            is TvLoginViewState.Error -> {
                title.text = state.errorTitle ?: getString(state.errorTitleRes)
                retryButton.setText(state.errorButtonLabelRes)
                loadingView.cancelAnimation()
            }
            is TvLoginViewState.Loading -> {
                startLoadingAnimation()
            }
            is TvLoginViewState.Success -> {
                if (loadingView.isAnimating)
                    finishLoadingAnimation()
                else
                    navigateToMain()
            }
        }
    }

    private fun updateCode(code: String) = with(binding.codeContainer) {
        var i = 0
        children.forEach {
            if (it.id != R.id.codeSeparator)
                (it as TextView).text = code[i++].toString()
        }
    }

    private fun startLoadingAnimation() = with(binding.loadingView) {
        setAnimation(R.raw.loading_animation)
        setMinAndMaxFrame(0, LOADING_ANIMATION_LOOP_END_FRAME)
        repeatCount = LottieDrawable.INFINITE
        repeatMode = LottieDrawable.RESTART
        playAnimation()
    }

    private fun finishLoadingAnimation() = with(binding.loadingView) {
        setMinAndMaxFrame(frame, LOADING_ANIMATION_FRAME_COUNT)
        repeatCount = 0
        playAnimation()
        onAnimationEnd {
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, TvMainActivity::class.java))
        finish()
    }

    companion object {
        const val LOADING_ANIMATION_LOOP_END_FRAME = 92
        const val LOADING_ANIMATION_FRAME_COUNT = 180
    }
}
