package com.juni.app

import android.app.Application
import com.juni.app.data.prefs.AppSettings
import com.juni.app.data.prefs.SecurePrefs

class JuniApp : Application() {

    lateinit var securePrefs: SecurePrefs
        private set

    lateinit var appSettings: AppSettings
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        securePrefs = SecurePrefs(this)
        appSettings = AppSettings(this)
    }

    companion object {
        private lateinit var instance: JuniApp

        fun get(): JuniApp = instance
    }
}
