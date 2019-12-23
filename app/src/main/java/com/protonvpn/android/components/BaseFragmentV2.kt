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
package com.protonvpn.android.components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModel
import dagger.android.support.AndroidSupportInjection
import dagger.android.support.DaggerFragment

abstract class BaseFragmentV2<VM : ViewModel, DB : ViewDataBinding> : DaggerFragment() {
    lateinit var viewModel: VM

    private var internalBinding: DB? = null
    protected val binding: DB
        get() = internalBinding
                ?: throw IllegalStateException("Accessing binding outside of lifecycle")

    fun init(inflater: LayoutInflater, container: ViewGroup) {
        internalBinding =
                DataBindingUtil.inflate(inflater, AnnotationParser.getAnnotatedLayout(this), container, false)
        binding.setLifecycleOwner { lifecycle }
    }

    open fun onViewCreated() {}

    abstract fun initViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidSupportInjection.inject(this)
        initViewModel()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        init(inflater, container!!)
        super.onCreateView(inflater, container, savedInstanceState)
        onViewCreated()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        internalBinding = null
    }
}
