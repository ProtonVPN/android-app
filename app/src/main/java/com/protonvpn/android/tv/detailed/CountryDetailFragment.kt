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
package com.protonvpn.android.tv.detailed

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
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
import com.protonvpn.android.models.features.PaidFeature
import com.protonvpn.android.tv.ui.TvKeyConstants
import com.protonvpn.android.tv.upsell.TvUpsellActivity
import com.protonvpn.android.utils.ViewUtils.requestAllFocus
import com.protonvpn.android.utils.setStartDrawable
import com.protonvpn.android.vpn.DisconnectTrigger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.core.presentation.utils.viewBinding
import me.proton.core.presentation.R as CoreR

@AndroidEntryPoint
class CountryDetailFragment : Fragment(R.layout.fragment_tv_country_details) {

    private val binding by viewBinding(FragmentTvCountryDetailsBinding::bind)
    private val viewModel: CountryDetailViewModel by viewModels()

    private var isPostponedTransition = false

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

        postponeEnterTransition()
        isPostponedTransition = true

        setupUi()
        viewModel.getState( getCountryCode())
            .onEach {updateState(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun setupUi() = with(binding) {
        flag.transitionName = transitionNameForCountry(getCountryCode())
        connectFastest.setOnClickListener(::onConnectClicked) // TODO: how is connectFastest different from connectStreaming?
        disconnect.setOnClickListener {
            viewModel.disconnect(DisconnectTrigger.Country("country details (TV)"))
        }
        openServerList.setOnClickListener {
            navigateToServerList()
        }
        defaultConnection.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAsDefaultCountry(isChecked, getCountryCode())
        }

        val streamingServices = viewModel.streamingServices(getCountryCode())
        if (streamingServices.isEmpty()) {
            streamingServicesContainer.isVisible = false
        } else {
            for (streamingService in streamingServices) {
                val streamingIcon = StreamingIcon(requireContext())
                streamingIcon.addStreamingView(streamingService)
                streamingServicesLayout.addView(streamingIcon)
            }
        }
    }

    private fun updateState(viewState: ViewState) {
        with(binding) {
            val card = viewState.countryCard

            countryName.text = card.countryName

            flag.setImageResource(card.backgroundImage.resId)
            if (isPostponedTransition) {
                flag.doOnPreDraw {
                    isPostponedTransition = false
                    startPostponedEnterTransition()
                }
            }
            countryDescription.setText(viewState.countryContentDescription)

            defaultConnection.isChecked = viewState.isDefaultCountry
            defaultConnection.isVisible = viewState.isAccessible

            connectStreaming.setStartDrawable(if (viewState.isPlusUser) 0 else CoreR.drawable.ic_proton_lock_filled)
            connectStreaming.setText(viewState.connectButtonText)
            connectStreaming.isVisible = viewState.showConnectToStreaming
            if (viewState.isPlusUser) {
                connectStreaming.setOnClickListener(::onConnectClicked)
            } else {
                connectStreaming.setOnClickListener(::onUpgradeClicked)
            }

            connectFastest.isVisible = viewState.showConnectFastest
            disconnect.isVisible = viewState.isConnectedToThisCountry
            disconnect.setText(viewState.disconnectButtonText)

            streamingServicesLayout.alpha = if (viewState.hasAccessToStreaming) 1f else 0.3f

            val focusOnButtons = buttons.findFocus() != null
            val emptyFocus = root.findFocus() == null
            if (focusOnButtons || emptyFocus) {
                if (viewState.showConnectButtons) {
                    if (viewState.hasAccessToStreaming)
                        binding.connectStreaming.requestAllFocus()
                    else
                        binding.connectFastest.requestAllFocus()
                } else {
                    binding.disconnect.requestAllFocus()
                }
            }
        }
    }

    private fun onConnectClicked(ignored: View) {
        viewModel.connect(requireActivity() as BaseTvActivity, getCountryCode())
    }

    private fun onUpgradeClicked(ignored: View) {
        val intent = Intent(context, TvUpsellActivity::class.java).apply {
            putExtra(TvKeyConstants.PAID_FEATURE, PaidFeature.AllCountries)
        }

        requireContext().startActivity(intent)
    }

    private fun getCountryCode(): String = requireNotNull(
        requireArguments().getString(EXTRA_COUNTRY_CODE)
    )

    private fun navigateToServerList() {
        val countryCode = getCountryCode()
        val bundle = Bundle().apply { putSerializable(TvServerListScreenFragment.EXTRA_COUNTRY, countryCode) }
        activity?.supportFragmentManager?.commit {
            setCustomAnimations(
                R.anim.slide_in_from_bottom, R.anim.slide_out_to_top,
                R.anim.slide_in_from_top, R.anim.slide_out_to_bottom)
            addSharedElement(binding.flag, transitionNameForCountry(countryCode))
            replace(R.id.home_container, TvServerListScreenFragment::class.java, bundle)
            addToBackStack(null)
        }
    }

    companion object {
        fun transitionNameForCountry(code: String) = "transition_$code"
        fun createArguments(countryCode: String) = Bundle().apply { putString(EXTRA_COUNTRY_CODE, countryCode) }

        private const val EXTRA_COUNTRY_CODE = "country_code"
    }
}
