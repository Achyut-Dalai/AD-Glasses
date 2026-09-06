package com.adglasses.app

import android.app.Application

class ADGlassesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.initialize(this)
    }
}
