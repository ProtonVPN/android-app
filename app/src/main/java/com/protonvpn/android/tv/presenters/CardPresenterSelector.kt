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
package com.protonvpn.android.tv.presenters

import android.content.Context
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.PresenterSelector
import com.protonvpn.android.tv.models.Card
import com.protonvpn.android.tv.models.CountryCard
import com.protonvpn.android.tv.models.IconCard
import com.protonvpn.android.tv.models.ConnectIntentCard
import com.protonvpn.android.tv.models.QuickConnectCard
import java.util.HashMap

class CardPresenterSelector(private val context: Context) : PresenterSelector() {

    private val presenters = HashMap<Class<*>, Presenter>()

    override fun getPresenter(item: Any): Presenter {
        require(item is Card) { "Expected item of type '${Card::class.java.name}'" }

        return presenters.getOrPut(item.javaClass) {
            when (item) {
                is IconCard -> IconCardPresenter(context)
                is QuickConnectCard -> TvItemPresenter(context)
                is ConnectIntentCard -> TvItemPresenter(context)
                is CountryCard -> TvItemPresenter(context)
            }
        }
    }
}
