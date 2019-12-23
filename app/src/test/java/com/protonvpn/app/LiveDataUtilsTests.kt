/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.app

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.protonvpn.android.utils.eagerMapNotNull
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class LiveDataUtilsTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @Test
    fun testEagerMapNotNull() {
        val data = MutableLiveData<Int>()
        val mapped = data.eagerMapNotNull { (it ?: 0) + 100 }
        Assert.assertEquals(100, mapped.value)
        data.value = 5

        Assert.assertEquals(105, mapped.value)

        data.value = null

        val observer = Observer<Int> {
            Assert.assertEquals(100, it)
        }
        mapped.observeForever(observer)

        Assert.assertEquals(100, mapped.value)
        data.removeObserver(observer)
    }
}
