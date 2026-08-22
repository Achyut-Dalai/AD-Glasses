package com.ad_glasses.shared.platform

/**
 * Cross-platform logging abstraction.
 * Android uses android.util.Log; iOS uses NSLog/OSLog.
 */
expect object PlatformLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable?)
    fun e(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable?)
}
