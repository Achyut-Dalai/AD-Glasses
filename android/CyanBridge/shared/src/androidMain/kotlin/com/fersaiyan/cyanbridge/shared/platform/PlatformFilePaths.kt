package com.fersaiyan.cyanbridge.shared.platform

import android.os.Environment
import java.io.File

actual object PlatformFilePaths {
    actual fun dataDirectory(): String = Environment.getDataDirectory().absolutePath
    actual fun cacheDirectory(): String {
        val ctx = getAppContext()
        return ctx.cacheDir.absolutePath
    }
    actual fun tempDirectory(): String = System.getProperty("java.io.tmpdir") ?: "/tmp"
    actual fun separator(): String = File.separator

    private fun getAppContext(): android.content.Context {
        return try {
            val clazz = Class.forName("com.fersaiyan.cyanbridge.ui.MyApplication")
            val app = clazz.getMethod("getInstance").invoke(null)
            app as android.content.Context
        } catch (_: Exception) {
            throw IllegalStateException("Android context not available. Ensure MyApplication is initialized.")
        }
    }
}
