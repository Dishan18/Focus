package com.regain.app

import android.app.Application
import com.regain.app.core.Haptics
import com.regain.app.data.AppCatalog
import com.regain.app.data.Store
import com.regain.app.data.UsageRepository

class RegainApp : Application() {

    lateinit var usage: UsageRepository
        private set
    lateinit var catalog: AppCatalog
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        Store.init(this)
        Haptics.init(this)
        Haptics.enabled = Store.hapticsOn
        usage = UsageRepository(this)
        catalog = AppCatalog(this)
    }

    companion object {
        lateinit var instance: RegainApp
            private set
    }
}
