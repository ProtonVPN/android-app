package com.protonvpn.data

import kotlin.time.Duration.Companion.seconds

object Timeouts {
    val FIVE_SECONDS = 5.seconds
    val TEN_SECONDS = 10.seconds
    val TWENTY_SECONDS = 20.seconds
    val ONE_MINUTE = 60.seconds

    val FIVE_SECONDS_MS = FIVE_SECONDS.inWholeMilliseconds
    val TEN_SECONDS_MS = TEN_SECONDS.inWholeMilliseconds
    val TWENTY_SECONDS_MS = TWENTY_SECONDS.inWholeMilliseconds
    val ONE_MINUTE_MS = ONE_MINUTE.inWholeMilliseconds
}
