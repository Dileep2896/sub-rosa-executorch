package com.subrosa.app

import android.app.Application
import com.subrosa.app.di.AppContainer

/** Application entry point. Owns the manual-DI [AppContainer] for the process lifetime. */
class SubRosaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
