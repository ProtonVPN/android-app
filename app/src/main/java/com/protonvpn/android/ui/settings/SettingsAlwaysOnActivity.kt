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
package com.protonvpn.android.ui.settings

import android.os.Bundle
import android.text.SpannableString
import android.text.style.ImageSpan
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityAlwaysOnBinding
import com.protonvpn.android.databinding.FragmentAlwaysOnStepBinding
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.getThemeColor
import com.protonvpn.android.utils.openVpnSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.proton.core.util.kotlin.DispatcherProvider
import javax.inject.Inject

@AndroidEntryPoint
@RequiresApi(24)
class SettingsAlwaysOnActivity : BaseActivityV2() {

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAlwaysOnBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initToolbarWithUpEnabled(binding.appbar.toolbar)

        with(binding.content) {
            indicator.tintIndicator(
                getThemeColor(R.attr.proton_icon_accent),
                getThemeColor(R.attr.proton_interaction_weak)
            )

            buttonOpenVpnSettings.setOnClickListener { openVpnSettings() }

            val adapter = StepFragmentAdapter(this@SettingsAlwaysOnActivity)
            pagerScreens.adapter = adapter
            indicator.setViewPager(pagerScreens)
            pagerScreens.registerOnPageChangeCallback(
                ButtonVisibilityUpdater(buttonPrevious, buttonNext, pagerScreens)
            )

            buttonPrevious.setOnClickListener {
                // The index is automatically clamped.
                pagerScreens.setCurrentItem(pagerScreens.currentItem - 1, true)
            }
            buttonNext.setOnClickListener {
                // The index is automatically clamped.
                pagerScreens.setCurrentItem(pagerScreens.currentItem + 1, true)
            }
        }
        preloadDrawables()
    }

    private fun preloadDrawables() {
        lifecycleScope.launch(dispatcherProvider.Io) {
            arrayOf(
                R.drawable.always_on_step_2,
                R.drawable.always_on_step_3,
                R.drawable.always_on_step_4
            ).forEach {
                ContextCompat.getDrawable(this@SettingsAlwaysOnActivity, it)
            }
        }
    }

    private class StepFragmentAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        private val constructors =
            arrayOf(
                SettingsAlwaysOnActivity::StepFragment1,
                SettingsAlwaysOnActivity::StepFragment2,
                SettingsAlwaysOnActivity::StepFragment3,
                SettingsAlwaysOnActivity::StepFragment4
            )

        override fun getItemCount(): Int = constructors.size

        override fun createFragment(position: Int): Fragment = constructors[position]()
    }

    class StepFragment1 : StepFragment(R.drawable.always_on_step_1, R.id.textStep1)
    class StepFragment2 : StepFragment(R.drawable.always_on_step_2, R.id.textStep2)
    class StepFragment3 : StepFragment(R.drawable.always_on_step_3, R.id.textStep3)
    class StepFragment4 : StepFragment(R.drawable.always_on_step_4, R.id.textStep4)

    abstract class StepFragment(
        @DrawableRes private val image: Int,
        @IdRes private val visibleText: Int
    ) : Fragment(R.layout.fragment_always_on_step) {

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val binding = FragmentAlwaysOnStepBinding.bind(view)
            binding.image.setImageResource(image)
            binding.textStep1.text = getCaption(R.string.settingsAlwaysOnWindowStep1)

            val iconSize = binding.textStep2.textSize * ICON_SIZE_RATIO
            val step2Text = SpannableString(getCaption(R.string.settingsAlwaysOnWindowStep2))
            step2Text.insertDrawable("%1\$s", R.drawable.ic_proton_cog_wheel, iconSize)
            binding.textStep2.text = step2Text

            binding.textStep3.text = getCaption(R.string.settingsAlwaysOnWindowStep3)
            binding.textStep4.text = getCaption(R.string.settingsAlwaysOnWindowStep4)
            binding.root.findViewById<View>(visibleText).visibility = View.VISIBLE
        }

        private fun getCaption(@StringRes text: Int): CharSequence = HtmlTools.fromHtml(getString(text))

        private fun SpannableString.insertDrawable(
            placeholder: String,
            @DrawableRes drawableRes: Int,
            sizePx: Float
        ) {
            val start = indexOf(placeholder)
            val drawable = ContextCompat.getDrawable(requireContext(), drawableRes)!!.mutate().apply {
                setBounds(0, 0, sizePx.toInt(), sizePx.toInt())
            }
            setSpan(ImageSpan(drawable), start, start + placeholder.length, ImageSpan.ALIGN_BOTTOM)
        }

        private companion object {
            const val ICON_SIZE_RATIO = 1.3f
        }
    }

    private class ButtonVisibilityUpdater(
        private val buttonPrevious: View,
        private val buttonNext: View,
        private val pager: ViewPager2
    ) : ViewPager2.OnPageChangeCallback() {

        init {
            onPageSelected(pager.currentItem)
        }

        override fun onPageSelected(position: Int) {
            buttonPrevious.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
            buttonNext.visibility = if (position == pager.adapter!!.itemCount - 1) View.INVISIBLE else View.VISIBLE
        }

        override fun onPageScrollStateChanged(state: Int) {
            if (state == ViewPager2.SCROLL_STATE_IDLE) {
                onPageSelected(pager.currentItem)
            } else {
                buttonPrevious.visibility = View.INVISIBLE
                buttonNext.visibility = View.INVISIBLE
            }
        }
    }
}
