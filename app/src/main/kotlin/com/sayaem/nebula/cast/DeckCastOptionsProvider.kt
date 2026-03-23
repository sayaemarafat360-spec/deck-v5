package com.sayaem.nebula.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.sayaem.nebula.MainActivity

class DeckCastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        // Default media receiver — works with any Chromecast without custom receiver app
        val receiverAppId = "CC1AD845"   // Default Media Receiver app ID

        val notifOptions = NotificationOptions.Builder()
            .setTargetActivityClassName(MainActivity::class.java.name)
            .build()

        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notifOptions)
            .setTargetActivityClassName(MainActivity::class.java.name)
            .build()

        return CastOptions.Builder()
            .setReceiverApplicationId(receiverAppId)
            .setCastMediaOptions(mediaOptions)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
