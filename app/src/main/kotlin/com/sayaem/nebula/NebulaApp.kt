package com.sayaem.nebula

import android.app.Application
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.*

class DeckApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Fix #10: Defer MobileAds initialisation off the main thread so the
        // launcher activity can be displayed before the AdMob SDK blocking call completes.
        CoroutineScope(Dispatchers.IO).launch {
            try { MobileAds.initialize(this@DeckApp) } catch (_: Exception) {}
        }
    }
}
