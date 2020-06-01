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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvFragment
import com.protonvpn.android.tv.main.TvMainActivity
import com.protonvpn.android.ui.login.LoginState
import com.protonvpn.android.ui.login.LoginViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

class TvLoginFragment : BaseTvFragment<LoginViewModel>(), Observer<LoginState> {
    private lateinit var userNameAction: GuidedAction
    private lateinit var passwordAction: GuidedAction
    private lateinit var loginAction: GuidedAction
    private lateinit var progressBarView: RelativeLayout
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun initViewModel() {
        viewModel = ViewModelProvider(this, viewModelFactory).get(LoginViewModel::class.java)
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        val title = getString(R.string.login)
        val description = getString(R.string.loginCreateAccount)
        val icon = resources.getDrawable(R.drawable.ic_proton, null)
        return Guidance(title, description, "", icon)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        userNameAction = GuidedAction.Builder(context).id(USER_ID).title("").descriptionInputType(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_CLASS_TEXT
        ).descriptionEditable(true).build()

        passwordAction =
            GuidedAction.Builder(context).id(PASSWORD_ID).title("").descriptionEditable(true)
                .descriptionInputType(
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
                ).build()
        loginAction = GuidedAction.Builder(context).id(LOGIN_ID).title(getString(R.string.login)).build()

        actions.add(userNameAction)
        actions.add(passwordAction)
        actions.add(loginAction)
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        if (action.id == LOGIN_ID) {
            hideKeyboard()
            login()
        }
    }

    fun login() {
        // TODO should get actually inputed values not label1
        val password = passwordAction.label1
        val username = userNameAction.label1
        if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            lifecycleScope.launch {
                viewModel.login(requireContext(), username.toString(), password.toString())
            }
        } else {
            showDialog(R.string.bugReportErrorEmptyEmail)
        }
    }

    private fun hideKeyboard() {
        val view = activity!!.currentFocus
        val imm = activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (view != null) {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun showDialog(message: Int, vararg params: Any) {
        Toast.makeText(activity, getString(message, *params), Toast.LENGTH_LONG).show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prepareProgressbar(view)
        viewModel.loginState.observe(viewLifecycleOwner, this)
    }

    private fun prepareProgressbar(view: View) {
        val rootView = view.findViewById<FrameLayout>(R.id.guidedstep_background_view_root)
        if (rootView != null) {
            progressBarView = createProgressbarRootView()
            progressBarView.visibility = View.GONE
            val progressBar = createProgressBar(view)
            progressBarView.addView(progressBar)
            rootView.addView(progressBarView)
        }
    }

    private fun createProgressbarRootView(): RelativeLayout {
        val relativeLayout = RelativeLayout(context)
        relativeLayout.setBackgroundColor(resources.getColor(R.color.colorPrimary))
        val layoutParameters = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT
        )
        relativeLayout.layoutParams = layoutParameters
        return relativeLayout
    }

    private fun createProgressBar(view: View): ProgressBar {
        val progressBar = ProgressBar(view.context)
        progressBar.isIndeterminate = true
        progressBar.indeterminateDrawable = resources.getDrawable(R.drawable.ic_proton, null)
        val layoutParams = RelativeLayout.LayoutParams(
            SPINNER_WIDTH, SPINNER_HEIGHT
        )
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT)
        progressBar.layoutParams = layoutParams
        return progressBar
    }

    override fun onChanged(loginState: LoginState?) {
        when (loginState) {
            is LoginState.Success -> {
                activity?.let {
                    val intent = Intent(it, TvMainActivity::class.java)
                    it.startActivity(intent)
                    activity?.finishAfterTransition()
                }
            }
            is LoginState.InProgress -> {
                progressBarView.isVisible = true
            }
            is LoginState.GuestHoleActivated -> {
                progressBarView.isVisible = true
            }
            is LoginState.Error -> {
                progressBarView.isVisible = false
                showDialog(R.string.error, loginState.error.toString())
            }
            is LoginState.UnsupportedAuth -> {
                progressBarView.isVisible = false
                showDialog(R.string.toastLoginAuthVersionError)
            }
        }
    }

    companion object {
        private const val LOGIN_ID = 2L
        private const val USER_ID = 3L
        private const val PASSWORD_ID = 4L
        private const val SPINNER_HEIGHT = 120
        private const val SPINNER_WIDTH = 120
    }
}
