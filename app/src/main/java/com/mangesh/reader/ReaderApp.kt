package com.mangesh.reader

import android.app.Application
import com.mangesh.reader.data.Store
import java.io.File

class ReaderApp : Application() {
    lateinit var store: Store
        private set
    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(File(filesDir, "store"))
        settings = Settings(this)
    }
}
