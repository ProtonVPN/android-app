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
package com.protonvpn.android.components

import android.os.Bundle
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import com.protonvpn.android.utils.whenCancelled

abstract class BaseTvBrowseFragment : BrowseSupportFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        headersState = HEADERS_DISABLED
    }

    override fun onDestroyView() {
        adapter = null
        super.onDestroyView()
    }

    protected fun ArrayObjectAdapter.addOrReplace(index: Int, row: Row) {
        if (size() > index)
            replace(index, row)
        else
            add(row)
    }

    protected abstract inner class FadeListRowPresenter(private val animateAlpha: Boolean) : ListRowPresenter() {

        private var selectedHolder: RowPresenter.ViewHolder? = null

        init {
            shadowEnabled = false
        }

        abstract fun rowAlpha(index: Int, selectedIdx: Int): Float
        abstract fun RowPresenter.ViewHolder.getRowIndex(): Int

        private fun RowPresenter.ViewHolder.setupAlpha(animated: Boolean) {
            val index = getRowIndex()
            val selectedIdx = selectedHolder?.getRowIndex() ?: index
            val targetAlpha = rowAlpha(index, selectedIdx)
            if (animated) {
                view.animate()
                    .alpha(targetAlpha)
                    .setDuration(ROW_FADE_DURATION)
                    .whenCancelled { setupAlpha(false) }
            } else
                view.alpha = targetAlpha
        }

        override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
            super.onBindRowViewHolder(holder, item)
            holder.setupAlpha(false)
        }

        override fun onUnbindRowViewHolder(holder: RowPresenter.ViewHolder) {
            if (holder == selectedHolder)
                selectedHolder = null

            super.onUnbindRowViewHolder(holder)
        }

        private fun updateRowsAlpha() {
            (0 until adapter.size()).forEach { i ->
                rowsSupportFragment.getRowViewHolder(i)?.setupAlpha(animateAlpha)
            }
        }

        override fun onRowViewSelected(holder: RowPresenter.ViewHolder, selected: Boolean) {
            super.onRowViewSelected(holder, selected)

            if (selected) {
                selectedHolder = holder
                updateRowsAlpha()
            }
        }
    }

    companion object {
        private const val ROW_FADE_DURATION = 300L
    }
}
