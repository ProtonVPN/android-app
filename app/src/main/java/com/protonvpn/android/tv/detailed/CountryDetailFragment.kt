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
package com.protonvpn.android.tv.detailed

import android.os.Bundle
import android.view.View
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.transition.ChangeBounds
import androidx.transition.ChangeClipBounds
import androidx.transition.ChangeImageTransform
import androidx.transition.ChangeTransform
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionSet
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.components.StreamingIcon
import com.protonvpn.android.databinding.FragmentTvCountryDetailsBinding
import com.protonvpn.android.tv.main.TvMainViewModel
import com.protonvpn.android.tv.models.CountryCard
import com.protonvpn.android.utils.ViewUtils.initLolipopButtonFocus
import com.protonvpn.android.utils.ViewUtils.requestAllFocus
import com.protonvpn.android.utils.setStartDrawable
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CountryDetailFragment : Fragment(R.layout.fragment_tv_country_details) {

    private val binding by viewBinding(FragmentTvCountryDetailsBinding::bind)
    private val viewModel: TvMainViewModel by viewModels()

    private lateinit var card: CountryCard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterTransition = TransitionSet().apply {
            addTransition(Fade(Fade.OUT))
            addTransition(Slide())
            addTransition(Fade(Fade.IN))
        }

        sharedElementEnterTransition = TransitionSet().apply {
            addTransition(ChangeBounds())
            addTransition(ChangeTransform())
            addTransition(ChangeImageTransform())
            addTransition(ChangeClipBounds())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
    }

    private fun setupUi() = with(binding) {
        val extras = arguments
        if (extras != null && extras.containsKey(EXTRA_COUNTRY_CODE)) {
            val countryCode = extras.getString(EXTRA_COUNTRY_CODE)
            card = requireNotNull(
                viewModel.getCountryCard(requireContext(), requireNotNull(countryCode))
            )
        }

        postponeEnterTransition()
        countryName.text = card.countryName

        card.backgroundImage?.let {
            flag.transitionName = transitionNameForCountry(card.vpnCountry.flag)
            flag.setImageResource(it.resId)
            flag.doOnPreDraw {
                startPostponedEnterTransition()
            }
        }

        defaultConnection.initLolipopButtonFocus()
        defaultConnection.isChecked = viewModel.isDefaultCountry(card.vpnCountry)
        defaultConnection.isVisible = viewModel.hasAccessibleServers(card.vpnCountry)
        defaultConnection.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAsDefaultCountry(isChecked, card.vpnCountry)
        }

        connectStreaming.initLolipopButtonFocus()
        connectStreaming.setStartDrawable(if (viewModel.isPlusUser()) 0 else R.drawable.ic_proton_lock_filled)
        connectStreaming.setOnClickListener {
            if (viewModel.isPlusUser()) {
                viewModel.connect(requireActivity() as BaseTvActivity, card)
            } else {
                viewModel.onUpgradeClicked(requireContext())
            }
        }
        if (viewModel.isPlusUser())
            connectStreaming.setText(R.string.tv_detail_connect)

        connectFastest.initLolipopButtonFocus()
        connectFastest.setOnClickListener {
            viewModel.connect(requireActivity() as BaseTvActivity, card)
        }

        disconnect.initLolipopButtonFocus()
        disconnect.setOnClickListener {
            viewModel.disconnect("country details (TV)")
        }

        openServerList.initLolipopButtonFocus()
        openServerList.setOnClickListener {
            navigateToServerList()
        }

        countryDescription.setText(viewModel.getCountryDescription(card.vpnCountry))
        val dimStreamingIcons = !viewModel.isPlusUser()

        val streamingServices = viewModel.streamingServices(card.vpnCountry.flag)
        if (streamingServices.isEmpty()) {
            streamingServicesContainer.isVisible = false
        } else {
            for (streamingService in streamingServices) {
                val streamingIcon = StreamingIcon(requireContext())
                if (dimStreamingIcons)
                    streamingIcon.alpha = 0.3f
                streamingIcon.addStreamingView(streamingService)
                streamingServicesLayout.addView(streamingIcon)
            }
        }

        viewModel.vpnStatus.observe(viewLifecycleOwner, Observer {
            updateButtons()
        })
    }

    private fun navigateToServerList() {
        val bundle = Bundle().apply { putSerializable(TvServerListScreenFragment.EXTRA_COUNTRY, card.vpnCountry.flag) }
        activity?.supportFragmentManager?.commit {
            setCustomAnimations(
                R.anim.slide_in_from_bottom, R.anim.slide_out_to_top,
                R.anim.slide_in_from_top, R.anim.slide_out_to_bottom)
            addSharedElement(binding.flag, transitionNameForCountry(card.vpnCountry.flag))
            replace(R.id.container, TvServerListScreenFragment::class.java, bundle)
            addToBackStack(null)
        }
    }

    private fun updateButtons() {
        val showConnectButtons = viewModel.showConnectButtons(card)
        val focusOnButtons = binding.buttons.findFocus() != null
        val emptyFocus = binding.root.findFocus() == null

        binding.connectFastest.isVisible = viewModel.showConnectToFastest(card)
        binding.connectStreaming.isVisible = viewModel.showConnectToStreamingButton(card)
        binding.disconnect.isVisible = viewModel.isConnectedToThisCountry(card)
        binding.disconnect.setText(viewModel.disconnectText(card))

        if (focusOnButtons || emptyFocus) {
            if (showConnectButtons) {
                if (viewModel.haveAccessToStreaming)
                    binding.connectStreaming.requestAllFocus()
                else
                    binding.connectFastest.requestAllFocus()
            } else {
                binding.disconnect.requestAllFocus()
            }
        }
    }

    companion object {
        fun transitionNameForCountry(code: String) = "transition_$code"
        fun createArguments(countryCode: String) = Bundle().apply { putString(EXTRA_COUNTRY_CODE, countryCode) }

        private const val EXTRA_COUNTRY_CODE = "country_code"
    }
}
