package com.achyut.adglasses.shared.platform

import platform.Foundation.NSLog

actual object PlatformLogger {
    actual fun d(tag: String, message: String) = NSLog("[DEBUG] %@: %@", tag, message)
    actual fun i(tag: String, message: String) = NSLog("[INFO] %@: %@", tag, message)
    actual fun w(tag: String, message: String) = NSLog("[WARN] %@: %@", tag, message)
    actual fun w(tag: String, message: String, throwable: Throwable?) {
        NSLog("[WARN] %@: %@ (%@)", tag, message, throwable?.message ?: "unknown")
    }
    actual fun e(tag: String, message: String) = NSLog("[ERROR] %@: %@", tag, message)
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        NSLog("[ERROR] %@: %@ (%@)", tag, message, throwable?.message ?: "unknown")
    }
}
