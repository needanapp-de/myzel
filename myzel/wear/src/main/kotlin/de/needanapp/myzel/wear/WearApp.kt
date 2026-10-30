package de.needanapp.myzel.wear

import android.app.Application

class WearApp : Application() {
  override fun onCreate() {
    super.onCreate()
    WatchLink.init(this)
    // Sitzungen eines früheren Prozesses kann niemand mehr zu Ende führen
    packageManager.packageInstaller.mySessions.forEach {
      packageManager.packageInstaller.abandonSession(it.sessionId)
    }
  }
}
