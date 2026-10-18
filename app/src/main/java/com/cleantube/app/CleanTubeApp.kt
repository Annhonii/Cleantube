package com.cleantube.app

import android.app.Application
import com.cleantube.app.data.Graph

class CleanTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
