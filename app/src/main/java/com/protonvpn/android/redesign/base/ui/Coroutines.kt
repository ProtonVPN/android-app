package com.protonvpn.android.redesign.base.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun <T> Flow<T>.collectAsEffect(block: suspend (T) -> Unit) {
    LaunchedEffect(this) {
        onEach(block).launchIn(this)
    }
}