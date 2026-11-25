package com.slncloth.shop.core.util

import java.time.Clock

/** App-wide clock abstraction so billing and tests can control time. */
object AppClock {
    val system: Clock = Clock.systemDefaultZone()
}
