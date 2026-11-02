package com.uphyca.nfctaglaunch

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * NFC タグからの起動用 alias の登録を NFC サービスに作り直させる。
 *
 * NFC サービスは `ACTION_TECH_DISCOVERED` を受け取る Activity の一覧をキャッシュしていて、
 * 作り直すのは `PACKAGE_ADDED / CHANGED / REMOVED` などを受けたときだけ。
 * stopped 状態（未起動・強制停止）の変化では作り直されないため、
 * インストール直後に作られたキャッシュは、ユーザーがアプリを起動しても「stopped」のまま残り、
 * Android 17 ではタグのインテントが配送されない。
 *
 * そこでプロセスの起動時に、alias の保存値を DEFAULT と ENABLED の間で切り替えて
 * `ACTION_PACKAGE_CHANGED` を起こす。マニフェストの既定が有効なので、どちらの値でも alias は有効のまま。
 * 同じ値を設定し直してもブロードキャストは飛ばないため、必ず交互に切り替える。
 */
object NfcLaunchRegistration {

    private const val TAG = "NfcTagLaunch"

    /** プロセスの起動時（[android.app.Application.onCreate]）に呼ぶ。 */
    fun refresh(context: Context) {
        val alias = ComponentName(context, context.packageName + ".NfcLaunchActivity")
        val pm = context.packageManager
        val next = when (val current = pm.getComponentEnabledSetting(alias)) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            else -> {
                // ユーザーが NFC からの起動をオフにしている（DISABLED）。そのままにする。
                Log.i(TAG, "refresh: skip (state=$current)")
                return
            }
        }
        pm.setComponentEnabledSetting(alias, next, PackageManager.DONT_KILL_APP)
        Log.i(TAG, "refresh: NfcLaunchActivity state -> $next")
    }
}
