package com.ctr04.touchpad

import android.app.Application
import com.ctr04.touchpad.common.injections.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TouchpadApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TouchpadApplication)
            modules(appModules)
        }
    }
}