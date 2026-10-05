# Opencode Android

opencode v2 サーバ用の専用Androidクライアント。サーバのWebUIを全画面表示する。
URLバー等のブラウザ要素は出さない。

## 機能

- 初回にサーバURLか `opencode pair` のペアリングリンクを入力して接続 (Cookieでログイン維持)
- フルスクリーンWebUI表示、長押しメニュー無効、外部リンクは他アプリで開く
- タブメニュー内に「App settings」「Reload」ボタンを追加 (WebUIと同スタイル、設定でON/OFF)
- ネイティブ設定画面: サーバ変更、ログアウト、外観修正 (カスタムCSS/JS注入)
- Share Intent受け取り (入力欄へ貼り付け試行、不可時はクリップボード)

## 必要なもの

- Android 8.0以上
- 到達可能なopencode v2サーバ (`opencode pair` でペアリングリンクを発行)

## インストール

GitHub ReleasesのAPKを端末に送ってインストール。「提供元不明のアプリ」を許可すること。

## ビルド

```sh
export JAVA_HOME=<JDK 17>
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

リリースビルドには署名鍵が必要:

```sh
export OPENCODE_KEYSTORE_FILE=$HOME/.android/opencode-release.keystore
export OPENCODE_KEYSTORE_PASSWORD=...
./gradlew :app:assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

新規に鍵を作る場合:

```sh
keytool -genkeypair -keystore ~/.android/opencode-release.keystore \
  -alias opencode -keyalg RSA -keysize 2048 -validity 9125
```

注意: 公開後の更新は同一鍵で署名しないと上書き更新できない。鍵とパスワードの保管は自己責任。

## プライバシー

- 通信は設定した自サーバとの間のみ。外部送信・解析・広告なし
- 認証Cookieは端末内のWebViewにのみ保存

## ライセンス

MIT
