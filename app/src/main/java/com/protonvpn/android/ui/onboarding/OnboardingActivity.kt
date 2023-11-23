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
package com.protonvpn.android.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityOnboardingBinding
import com.protonvpn.android.databinding.FragmentOnboardingConnectionBinding
import com.protonvpn.android.databinding.FragmentOnboardingTelemetryBinding
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.ui.planupgrade.UpgradeOnboardingDialogActivity
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.proton.core.presentation.utils.onClick
import me.proton.core.presentation.utils.viewBinding

class TelemetryConsent : Fragment(R.layout.fragment_onboarding_telemetry) {

    private val binding by viewBinding(FragmentOnboardingTelemetryBinding::bind)
    private val viewModel: OnboardingViewModel by viewModels({ requireActivity() })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            with(textTelemetryInfo) {
                text = HtmlTools.fromHtml(getString(R.string.settingsTelemetryScreenInfo, Constants.TELEMETRY_INFO_URL))
                movementMethod = LinkMovementMethod()
            }
            switchEnableTelemetry.isChecked = viewModel.telemetryEnabledSwitch
            switchEnableTelemetry.setOnCheckedChangeListener { _, isChecked ->
                viewModel.telemetryEnabledSwitch = isChecked
            }
            switchSendCrashReports.isChecked = viewModel.crashReportingSwitch
            switchSendCrashReports.setOnCheckedChangeListener { _, isChecked ->
                viewModel.crashReportingSwitch = isChecked
            }
        }
    }
}

class FirstConnection : Fragment(R.layout.fragment_onboarding_connection) {

    private val binding by viewBinding(FragmentOnboardingConnectionBinding::bind)
    private val viewModel: OnboardingViewModel by viewModels({ requireActivity() })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            val servers = viewModel.serversCount()
            val countries = viewModel.countriesCount()
            val serversString =
                resources.getQuantityString(R.plurals.onboarding_connection_description_servers, servers, servers)
            val countriesString =
                resources.getQuantityString(R.plurals.onboarding_connection_description_countries, countries, countries)
            binding.description.text =
                getString(R.string.onboarding_connection_description, serversString, countriesString)
        }
    }
}

@AndroidEntryPoint
class OnboardingActivity : BaseActivityV2() {

    private val viewModel: OnboardingViewModel by viewModels()
    private val binding by viewBinding(ActivityOnboardingBinding::inflate)

    inner class Step(
        val action: () -> Unit = ::navigateNext,
        @StringRes val actionText: Int = R.string.onboardingNext,
        val showConnect: Boolean = false,
        val canSkipOnboarding: Boolean = false,
        val createFragment: () -> Fragment,
    )

    private val steps = mutableListOf<Step>()

    private fun initSteps() {
        steps += Step(actionText = R.string.onboarding_welcome_action) {
            Fragment(R.layout.fragment_onboarding_welcome)
        }
        if (viewModel.showTelemetryPrompt) {
            steps += Step(
                action = {
                    viewModel.applyTelemetryChoice()
                    navigateNext()
                }
            ) {
                TelemetryConsent()
            }
        }

        steps += Step(actionText = R.string.onboading_connect_now, showConnect = true, canSkipOnboarding = true) {
            FirstConnection()
        }
    }

    inner class PagerAdapter : FragmentStateAdapter(this) {
        override fun getItemCount() = steps.size
        override fun createFragment(position: Int) = steps[position].createFragment()
    }

    private fun navigateNext() = with(binding.pager) {
        if (currentItem + 1 == adapter?.itemCount) {
            if (viewModel.isInAppUpgradeAllowed) {
                UpgradeOnboardingDialogActivity.launch(this@OnboardingActivity)
            }
            finish()
        } else {
            setCurrentItem(currentItem + 1, true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initSteps()

        with(binding) {
            skip.onClick { finish() }
            pager.adapter = PagerAdapter()
            pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val step = steps[position]
                    next.onClick { step.action() }
                    next.setText(step.actionText)
                    next.isVisible = !step.showConnect
                    skip.isVisible = step.canSkipOnboarding

                    connect.isVisible = step.showConnect
                }
            })
            connect.onClick {
                onConnect()
            }
        }
    }

    override fun retryConnection(profile: Profile) {
        onConnect()
    }

    private fun onConnect() = with(binding) {
        lifecycleScope.launch {
            connect.setText(R.string.onboading_connecting_now)
            connect.setLoading()
            val error = viewModel.connect(this@OnboardingActivity, getVpnUiDelegate())
            if (error == null) {
                startActivity(Intent(this@OnboardingActivity, CongratsActivity::class.java))
                finish()
            } else {
                val errorText = error.html?.let { HtmlTools.fromHtml(it).toString() }
                    ?: if (error.res != 0) getString(error.res) else null
                errorText?.let { snackbarHelper.errorSnack(it) }

                connect.setText(R.string.onboading_connect_now)
                connect.setIdle()
            }
        }
    }

    override fun onBackPressed() {
        with(binding.pager) {
            if (currentItem > 0)
                currentItem -= 1
            else
                super.onBackPressed()
        }
    }
}
