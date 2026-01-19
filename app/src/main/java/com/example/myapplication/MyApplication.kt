package com.example.myapplication

import android.app.Application
import com.example.myapplication.data.sample.SampleImageSeeder

class MyApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SampleImageSeeder.seedIfNeeded(this)
    }
}
