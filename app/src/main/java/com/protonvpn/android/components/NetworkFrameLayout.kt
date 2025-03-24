/*
 * Copyright (c) 2017 Proton AG
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
package com.protonvpn.android.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import com.protonvpn.android.R
import com.protonvpn.android.ui.login.TroubleshootActivity
import com.protonvpn.android.utils.preventClickTrough
import me.proton.core.network.domain.ApiResult
import me.proton.core.presentation.utils.errorSnack

class NetworkFrameLayout : RelativeLayout, LoaderUI {

    private val loadingView by lazy {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.fragment_app_loading, this, false).also {
            it.preventClickTrough()
            addView(it)
        }
    }

    private val textLoading by lazy {
        loadingView.findViewById<TextView>(R.id.textLoading)
    }

    private val retryView by lazy {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.fragment_retry_screen, this, false).also {
            it.findViewById<View>(R.id.buttonRetry).setOnClickListener {
                retryListener?.invoke()
            }
            it.preventClickTrough()
            addView(it)
        }
    }

    private var loadingTitle: String? = null
    private var retryListener: (() -> Unit)? = null
    override var state = State.EMPTY

    enum class State {
        LOADING, ERROR, EMPTY
    }

    override fun switchToRetry(error: ApiResult.Error) {
        state = State.ERROR
        switchToRetryView(error)
    }

    override fun switchToEmpty() {
        state = State.EMPTY
        switchToEmptyView()
    }

    override fun switchToLoading() {
        state = State.LOADING
        switchToLoadingView(loadingTitle)
    }

    fun switchToLoading(loadingTitle: String?) {
        state = State.LOADING
        switchToLoadingView(loadingTitle)
    }

    override fun setRetryListener(listener: () -> Unit) {
        retryListener = listener
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet) : super(context, attrs) {
        initAttrs(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initAttrs(attrs)
    }

    private fun initAttrs(attrs: AttributeSet) {
        val a =
                context.theme.obtainStyledAttributes(attrs, R.styleable.NetworkFrameLayout, 0, 0)
        loadingTitle = a.getString(R.styleable.NetworkFrameLayout_textLoading)
        a.recycle()
    }

    private fun switchToLoadingView(loadingTitle: String?) {
        textLoading.text = loadingTitle
        loadingView.isVisible = true
        retryView.isVisible = false
    }

    private fun switchToRetryView(error: ApiResult.Error) {
        loadingView.isVisible = false
        retryView.isVisible = true
        initRetryView(error)
    }

    private fun initRetryView(error: ApiResult.Error) {
        val textDescription = retryView.findViewById<TextView>(R.id.textDescription)
        val troubleshootButton = retryView.findViewById<TextView>(R.id.buttonTroubleshoot)
        val showTroubleshoot = error.isPotentialBlocking
        troubleshootButton.isVisible = showTroubleshoot
        if (showTroubleshoot) {
            troubleshootButton.setOnClickListener {
                context.startActivity(Intent(context, TroubleshootActivity::class.java))
            }
        }

        textDescription.setOnLongClickListener {
            // Not using SnackbarHelper - this layout covers the whole screen, so there's no need
            // for anchoring the Snackbar.
            it.errorSnack(error.debugMessage(), resources.getString(R.string.copy_to_clipboard), actionOnClick = {
                copyToClipboard(context, "Debug message", error.debugMessage())
            })
            true
        }

        textDescription.text = errorToMessage(error)
    }

    private fun ApiResult.Error.debugMessage() = cause?.message ?: toString()

    private fun errorToMessage(error: ApiResult.Error): String = when (error) {
        is ApiResult.Error.NoInternet -> context.getString(R.string.loaderErrorNoInternet)
        is ApiResult.Error.Http -> error.proton?.error ?: context.getString(R.string.loaderErrorGeneric)
        is ApiResult.Error.Timeout -> context.getString(R.string.loaderErrorTimeout)
        else -> context.getString(R.string.loaderErrorGeneric)
    }

    private fun switchToEmptyView() {
        loadingView.isVisible = false
        retryView.isVisible = false
    }

    private fun copyToClipboard(context: Context, label:String, text: String) {
        val clipData = ClipData.newPlainText(label, text)
        context.getSystemService<ClipboardManager>()?.setPrimaryClip(clipData)
    }
}
