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
package com.protonvpn.android.ui.drawer

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivityAlwaysOnBinding
import com.protonvpn.android.databinding.FragmentAlwaysOnStepBinding
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.getThemeColor

@ContentLayout(R.layout.activity_always_on)
@RequiresApi(24)
class AlwaysOnSettingsActivity : BaseActivityV2<ActivityAlwaysOnBinding, ViewModel>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbarWithUpEnabled(binding.appbar.toolbar)

        with(binding.content) {
            indicator.tintIndicator(
                getThemeColor(R.attr.brand_norm),
                getThemeColor(R.attr.proton_interaction_weak)
            )

            buttonOpenVpnSettings.setOnClickListener {
                startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
            }

            val adapter = StepFragmentAdapter(this@AlwaysOnSettingsActivity)
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
    }

    override fun initViewModel() {}

    private class StepFragmentAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        private val constructors =
            arrayOf(::StepFragment1, ::StepFragment2, ::StepFragment3, ::StepFragment4)

        override fun getItemCount(): Int = constructors.size

        override fun createFragment(position: Int): Fragment = constructors[position]()
    }

    class StepFragment1 : StepFragment(R.drawable.always_on_step_1, R.string.settingsAlwaysOnWindowStep1)
    class StepFragment2 : StepFragment(R.drawable.always_on_step_2, R.string.settingsAlwaysOnWindowStep2) {
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            insertDrawable(binding.textCaption, "%1\$s", R.drawable.ic_cog_wheel)
        }

        @Suppress("SameParameterValue")
        private fun insertDrawable(textView: TextView, placeholder: String, @DrawableRes drawableRes: Int) {
            val index = textView.text.indexOf(placeholder)
            val iconSize = (textView.textSize * 1.3).toInt()
            val drawable = ContextCompat.getDrawable(requireContext(), drawableRes)!!.apply {
                setBounds(0, 0, iconSize, iconSize)
            }
            val newText = SpannableStringBuilder(textView.text).apply {
                setSpan(ImageSpan(drawable, 0), index, index + placeholder.length, ImageSpan.ALIGN_BOTTOM)
            }
            textView.text = newText
        }
    }
    class StepFragment3 : StepFragment(R.drawable.always_on_step_3, R.string.settingsAlwaysOnWindowStep3)
    class StepFragment4 : StepFragment(R.drawable.always_on_step_4, R.string.settingsAlwaysOnWindowStep4)

    abstract class StepFragment(
        @DrawableRes private val image: Int,
        @StringRes private val text: Int
    ) : Fragment(R.layout.fragment_always_on_step) {

        // TODO: create (or import) a util for lifecycle-scoped ViewBinding
        private var internalBinding: FragmentAlwaysOnStepBinding? = null
        protected val binding: FragmentAlwaysOnStepBinding
            get() = internalBinding
                ?: throw IllegalStateException("Accessing binding outside of lifecycle")

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            internalBinding = FragmentAlwaysOnStepBinding.bind(view)
            binding.image.setImageResource(image)
            binding.textCaption.text = getCaption(text)
            binding.textLongest.text = getCaption(R.string.settingsAlwaysOnWindowStep4)
        }

        override fun onDestroyView() {
            internalBinding = null
            super.onDestroyView()
        }

        private fun getCaption(@StringRes text: Int): CharSequence = HtmlTools.fromHtml(getString(text))
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
