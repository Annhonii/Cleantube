package com.cleantube.app

import android.app.Application
import com.cleantube.app.data.Graph

class CleanTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        com.cleantube.app.data.Reco.init(this)
        com.cleantube.app.data.Yt.init(this)
        com.cleantube.app.data.Downloads.init(this)
        com.cleantube.app.data.Session.markLoggedIn()
        com.cleantube.app.data.UploadAlerts.sync(this, replace = false)
        // loads the extractor + YouTube player script once in the background, so the first video starts fast
        com.cleantube.app.data.Playback.warmUp()
    }
}
