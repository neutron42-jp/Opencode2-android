# Opencode2 Android

opencode v2 サーバ用の専用Androidクライアント。WebUIを全画面WebViewで表示し、
URLバー等のブラウザ要素を出さない。

## 構成

- `SetupActivity`: 初回サーバURL / ペアリングリンク入力
- `MainActivity`: 全画面WebViewホスト (Cookie永続、ファイル選択、共有受け取り)
- `ServerPrefs`: サーバURL・共有テキスト保存

## 接続

1. サーバ側で `opencode pair` を実行してペアリングリンクを取得
2. アプリのペアリングリンク欄に貼って接続 (Cookieで30日維持)
3. またはサーバURLを入力してWebUI側でパスワード入力

## ビルド

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-temurin-jdk
export ANDROID_HOME=$HOME/Android/Sdk
gradle :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```
