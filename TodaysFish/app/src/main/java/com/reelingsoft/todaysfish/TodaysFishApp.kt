package com.reelingsoft.todaysfish

import android.app.Application
import com.github.ajalt.timberkt.Timber


class TodaysFishApp : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}