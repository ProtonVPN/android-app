package com.protonvpn.data

import kotlin.time.Duration.Companion.seconds

object Timeouts {
    val SMALL = 10.seconds
    val MEDIUM = 20.seconds
    val LONG = 60.seconds

    val SMALL_MS = SMALL.inWholeMilliseconds
    val MEDIUM_MS = MEDIUM.inWholeMilliseconds
    val LONG_MS = LONG.inWholeMilliseconds
}
