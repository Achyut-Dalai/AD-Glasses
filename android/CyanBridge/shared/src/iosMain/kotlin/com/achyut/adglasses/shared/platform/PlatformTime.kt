package com.achyut.adglasses.shared.platform

import platform.Foundation.NSDate

actual fun platformCurrentTimeMillis(): Long =
    ((NSDate().timeIntervalSinceReferenceDate + 978307200.0) * 1000.0).toLong()
