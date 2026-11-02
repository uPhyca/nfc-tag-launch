# NFC Tag Launch

Android 16 / 17 で変わった NFC タグディスパッチの挙動を実機で確かめるための最小アプリです。**タグを読む機能はなく、OS からどのインテントがどの入口に届いたかを表示するだけ**です。

## 何を確かめるためのものか

- Android 17 で targetSdk 37 以上のアプリに課される `android.permission.DISPATCH_NFC_MESSAGE` の要件
- stopped 状態のアプリにはタグのインテントが配送されないこと、およびその判定が `TECH_DISCOVERED` 経路ではキャッシュ越しになること
- Android 16 で入った「NFC 経由での起動」の許可リスト（`isTagIntentAllowed()` / `ACTION_CHANGE_TAG_INTENT_PREFERENCE`）
- `setComponentEnabledSetting()` による NFC 起動の動的な有効 / 無効

## 構成

```
app/src/main/
  AndroidManifest.xml                    MainActivity（ディープリンク）+ 2 つの activity-alias
  res/xml/nfc_tech_filter.xml            TECH_DISCOVERED の tech-list（NfcF / NfcA / NfcB / NfcV / IsoDep / Ndef / NdefFormatable）
  kotlin/com/uphyca/nfctaglaunch/
    MainActivity.kt                      状態表示・alias の切り替え・設定画面への導線
    ReceivedIntents.kt                   受け取ったインテントの記録
```

| 入口 | 役割 |
| --- | --- |
| `.MainActivity` | ランチャーと `ACTION_VIEW` のディープリンク。権限では守らない |
| `.NfcLaunchActivity` | NFC 専用の alias。`android:permission="android.permission.DISPATCH_NFC_MESSAGE"` 付き。既定で有効 |
| `.NfcLaunchUnprotectedActivity` | 比較用。権限なしの alias。既定で無効 |

## ビルドと実行

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`compileSdk` / `targetSdk` は 37、`minSdk` は 26 です。**エミュレータには NFC がないので、確認は実機で行います。**

`adb shell pm enable|disable` で alias を切り替えたい場合は、テスト用パッケージとしてビルドして入れます。

```sh
./gradlew :app:assembleDebug -Pandroid.injected.testOnly=true
adb install -t -r app/build/outputs/apk/debug/app-debug.apk
```

## 観察の仕方

```sh
# ディスパッチャの判断
adb logcat -v time -s 'NfcDispatcher:*' 'RegisteredComponentCache:*' 'NfcTagLaunch:*'

# 許可リスト（TagAppPreference: 以下）
adb shell dumpsys nfc

# 「NFC 経由での起動」の設定画面
adb shell am start -a android.nfc.action.CHANGE_TAG_INTENT_PREFERENCE
```

FeliCa カード（Suica など）をかざすと `ACTION_TECH_DISCOVERED` 経路になります。Android 17 では初回は許可リストに「許可しない」で登録されるので、通知または設定画面から許可してから再度かざしてください。
