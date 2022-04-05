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
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
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
import com.protonvpn.android.databinding.FragmentOnboardingStepBinding
import com.protonvpn.android.databinding.OnboardingStepDotBinding
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.ui.planupgrade.UpgradePlusOnboardingDialogActivity
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.getThemeColor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.proton.core.presentation.utils.onClick
import me.proton.core.presentation.utils.viewBinding

class OnboardingStep : Fragment(R.layout.fragment_onboarding_step) {

    private val binding by viewBinding(FragmentOnboardingStepBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val index = arguments?.getInt(INDEX)!!
        val count = arguments?.getInt(COUNT)!!
        val plus = arguments?.getBoolean(PLUS) ?: false
        val titleRes = arguments?.getInt(TITLE)!!
        val descriptionRes = arguments?.getInt(DESC)!!
        val imageRes = arguments?.getInt(IMAGE)!!
        with(binding) {
            val brand = root.getThemeColor(R.attr.brand_norm)
            repeat(count) { i ->
                val dot = OnboardingStepDotBinding.inflate(layoutInflater, indicator, true)
                if (i == index)
                    dot.dot.setColorFilter(brand)
            }

            image.setImageResource(imageRes)
            availableOnPlus.isVisible = plus
            title.setText(titleRes)
            description.setText(descriptionRes)
        }
    }

    companion object {
        const val IMAGE = "image"
        const val TITLE = "title"
        const val DESC = "desc"
        const val INDEX = "index"
        const val COUNT = "count"
        const val PLUS = "plus"
    }
}

class FirstConnection : Fragment(R.layout.fragment_onboarding_connection) {

    private val binding by viewBinding(FragmentOnboardingConnectionBinding::bind)
    private val viewModel: OnboardingViewModel by viewModels({ requireActivity() })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            binding.description.text = getString(R.string.onboarding_connection_description,
                viewModel.serversCount(), viewModel.countriesCount())
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
        val createFragment: () -> Fragment,
    )

    private val steps = mutableListOf<Step>()

    private fun initSteps() {
        steps += Step(actionText = R.string.onboarding_welcome_action) {
            Fragment(R.layout.fragment_onboarding_welcome)
        }
        val dottedSteps = listOf(
            bundleOf(
                OnboardingStep.IMAGE to R.drawable.onboarding_protection_image,
                OnboardingStep.TITLE to R.string.onboarding_protection_title,
                OnboardingStep.DESC to R.string.onboarding_protection_description,
            ),
            bundleOf(
                OnboardingStep.IMAGE to R.drawable.onboarding_streaming_image,
                OnboardingStep.TITLE to R.string.onboarding_streaming_title,
                OnboardingStep.DESC to R.string.onboarding_streaming_description,
                OnboardingStep.PLUS to true,
            ),
            bundleOf(
                OnboardingStep.IMAGE to R.drawable.upgrade_netshield,
                OnboardingStep.TITLE to R.string.onboarding_netshield_title,
                OnboardingStep.DESC to R.string.onboarding_netshield_description,
                OnboardingStep.PLUS to true,
            ),
        )
        dottedSteps.forEachIndexed { i, args ->
            steps += Step {
                OnboardingStep().apply {
                    arguments = args.apply {
                        putInt(OnboardingStep.INDEX, i)
                        putInt(OnboardingStep.COUNT, dottedSteps.size)
                    }
                }
            }
        }
        if (viewModel.showConnect) {
            steps += Step(actionText = R.string.onboading_connect_now, showConnect = true) {
                FirstConnection()
            }
        }
    }

    inner class PagerAdapter : FragmentStateAdapter(this) {
        override fun getItemCount() = steps.size
        override fun createFragment(position: Int) = steps[position].createFragment()
    }

    private fun navigateNext() = with(binding.pager) {
        if (currentItem + 1 == adapter?.itemCount) {
            startActivity(Intent(this@OnboardingActivity, UpgradePlusOnboardingDialogActivity::class.java))
            finish()
        } else {
            setCurrentItem(currentItem + 1, true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        viewModel.init()

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
