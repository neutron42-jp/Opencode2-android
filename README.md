# Opencode Android

A dedicated Android client for an opencode v2 server. Shows the server WebUI
fullscreen, with no browser chrome.

opencode v2 サーバ用の専用Androidクライアント。サーバのWebUIを全画面表示する。
URLバー等のブラウザ要素は出さない。

## Features / 機能

- Connect with a server URL or an `opencode pair` pairing link (login kept via Cookie)
  - 初回にサーバURLか `opencode pair` のペアリングリンクを入力して接続 (Cookieでログイン維持)
- Fullscreen WebUI, no long-press menu, external links open in other apps
  - フルスクリーンWebUI表示、長押しメニュー無効、外部リンクは他アプリで開く
- Extra "App settings" / "Reload" buttons inside the tab menu, styled like stock
  buttons (toggle in Settings)
  - タブメニュー内に「App settings」「Reload」ボタンを追加 (WebUIと同スタイル、設定でON/OFF)
- Native settings screen: change server, logout, appearance fixes (custom CSS/JS injection)
  - ネイティブ設定画面: サーバ変更、ログアウト、外観修正 (カスタムCSS/JS注入)
- Accepts Share Intents (tries to paste into the input field, falls back to clipboard)
  - Share Intent受け取り (入力欄へ貼り付け試行、不可時はクリップボード)

## Requirements / 必要なもの

- Android 8.0+
- A reachable opencode v2 server (issue a pairing link with `opencode pair`)
  - 到達可能なopencode v2サーバ (`opencode pair` でペアリングリンクを発行)

## Install / インストール

Install the APK from GitHub Releases on the device ("install unknown apps" must be allowed).

GitHub ReleasesのAPKを端末に送ってインストール。「提供元不明のアプリ」を許可すること。

## Build / ビルド

```sh
export JAVA_HOME=<JDK 17>
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

A release build needs a signing key / リリースビルドには署名鍵が必要:

```sh
export OPENCODE_KEYSTORE_FILE=$HOME/.android/opencode-release.keystore
export OPENCODE_KEYSTORE_PASSWORD=...
./gradlew :app:assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

To generate a new key / 新規に鍵を作る場合:

```sh
keytool -genkeypair -keystore ~/.android/opencode-release.keystore \
  -alias opencode -keyalg RSA -keysize 2048 -validity 9125
```

Note: updates must be signed with the same key or they cannot overwrite-install.
Keep the key and password safe / 公開後の更新は同一鍵で署名しないと上書き更新できない。鍵とパスワードの保管は自己責任。

## Privacy / プライバシー

- Talks only to your configured server. No analytics, no ads
  - 通信は設定した自サーバとの間のみ。外部送信・解析・広告なし
- Auth cookies stay inside the on-device WebView
  - 認証Cookieは端末内のWebViewにのみ保存

## License / ライセンス

MIT
