package com.uphyca.nfctaglaunch

import android.app.Application

class NfcTagLaunchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // コールドスタートのたびに NFC サービス側のキャッシュを作り直させる。
        NfcLaunchRegistration.refresh(this)
    }
}
