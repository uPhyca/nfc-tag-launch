package com.uphyca.nfctaglaunch

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val TAG = "NfcTagLaunch"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) record(intent)
        setContent {
            MaterialTheme {
                App()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        record(intent)
    }

    private fun record(intent: Intent) {
        if (intent.action == Intent.ACTION_MAIN) return
        Log.i(TAG, "received: action=${intent.action} data=${intent.dataString} component=${intent.component}")
        ReceivedIntents.add(intent)
    }
}

/** NFC からの起動を受け付ける入口。既定で有効。 */
private val PROTECTED_ALIAS = ".NfcLaunchActivity" to true

/** 比較用の、権限で守っていない入口。既定で無効。 */
private val UNPROTECTED_ALIAS = ".NfcLaunchUnprotectedActivity" to false

private fun Context.aliasComponent(shortName: String): ComponentName =
    ComponentName(this, packageName + shortName)

/** 上書き設定が DEFAULT のときはマニフェストの既定値を採用する。 */
private fun Context.isAliasEnabled(alias: Pair<String, Boolean>): Boolean =
    when (packageManager.getComponentEnabledSetting(aliasComponent(alias.first))) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
        else -> alias.second
    }

/**
 * 切り替えは永続化される（アプリ更新・プロセス終了・再起動後も残る）。
 * 起動のたびに設定し直す必要はない。
 */
private fun Context.setAliasEnabled(alias: Pair<String, Boolean>, enabled: Boolean) {
    packageManager.setComponentEnabledSetting(
        aliasComponent(alias.first),
        if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
    )
    Log.i(TAG, "${alias.first} enabled=$enabled")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val received by ReceivedIntents.items.collectAsState()

    var protectedEnabled by remember { mutableStateOf(context.isAliasEnabled(PROTECTED_ALIAS)) }
    var unprotectedEnabled by remember { mutableStateOf(context.isAliasEnabled(UNPROTECTED_ALIAS)) }
    var nfcEnabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    var tagIntentAllowed by remember { mutableStateOf<Boolean?>(null) }

    // 設定画面から戻ってきたときに最新の状態を読み直す。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                nfcEnabled = adapter?.isEnabled == true
                tagIntentAllowed = readTagIntentAllowed(adapter)
                protectedEnabled = context.isAliasEnabled(PROTECTED_ALIAS)
                unprotectedEnabled = context.isAliasEnabled(UNPROTECTED_ALIAS)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("NFC Tag Launch") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("状態", style = MaterialTheme.typography.titleMedium)
                        Text("targetSdk: ${context.applicationInfo.targetSdkVersion} / 端末 API: ${Build.VERSION.SDK_INT}")
                        Text("NFC: " + when {
                            adapter == null -> "非対応"
                            nfcEnabled -> "有効"
                            else -> "無効"
                        })
                        Text("isTagIntentAllowed: " + when (tagIntentAllowed) {
                            null -> "API 36 未満のため取得不可"
                            true -> "true（タグのインテントを受け取れる）"
                            false -> "false（ユーザーが「許可しない」にしている）"
                        })
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("NFC からの起動", style = MaterialTheme.typography.titleMedium)
                        ToggleRow(
                            title = "受け付ける（DISPATCH_NFC_MESSAGE で保護した alias）",
                            checked = protectedEnabled,
                        ) {
                            context.setAliasEnabled(PROTECTED_ALIAS, it)
                            protectedEnabled = context.isAliasEnabled(PROTECTED_ALIAS)
                        }
                        ToggleRow(
                            title = "比較用: 権限なしの alias を有効にする",
                            checked = unprotectedEnabled,
                        ) {
                            context.setAliasEnabled(UNPROTECTED_ALIAS, it)
                            unprotectedEnabled = context.isAliasEnabled(UNPROTECTED_ALIAS)
                        }
                        if (Build.VERSION.SDK_INT >= 36) {
                            OutlinedButton(onClick = { openTagIntentPreference(context) }) {
                                Text("「NFC 経由での起動」の設定を開く")
                            }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("受け取ったインテント（${received.size}）", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { ReceivedIntents.clear() }) { Text("消去") }
                }
            }
            items(received) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${item.receivedAt}  ${item.action?.removePrefix("android.nfc.action.")?.removePrefix("android.intent.action.")}", style = MaterialTheme.typography.titleSmall)
                        Text("入口: ${item.component ?: "-"}")
                        item.data?.let { Text("data: $it") }
                        item.ndef?.let { Text("NDEF: $it") }
                        if (item.techs.isNotEmpty()) Text("tech: ${item.techs.joinToString()}")
                        item.tagId?.let { Text("id: $it") }
                    }
                }
            }
            item { HorizontalDivider() }
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Android 16（API 36）以降。ユーザーが「NFC 経由での起動」でこのアプリを許可しているか。 */
private fun readTagIntentAllowed(adapter: NfcAdapter?): Boolean? {
    if (adapter == null || Build.VERSION.SDK_INT < 36) return null
    return runCatching { adapter.isTagIntentAllowed() }
        .onFailure { Log.w(TAG, "isTagIntentAllowed failed", it) }
        .getOrNull()
}

/** 設定 > アプリ > 特別なアプリアクセス > NFC 経由での起動 を開く。 */
private fun openTagIntentPreference(context: Context) {
    if (Build.VERSION.SDK_INT < 36) return
    try {
        context.startActivity(Intent(NfcAdapter.ACTION_CHANGE_TAG_INTENT_PREFERENCE))
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "settings not found", e)
    }
}
