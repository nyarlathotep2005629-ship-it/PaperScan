# PaperScan 開発環境構築記録

確認日: 2026-10-07（JST）

## 作成した場所

```text
C:\Users\hiroki\Documents\Codex\PaperScan
```

作成済みの作業領域:

- `docs/` 設計・要件資料
- `work/` 作業用ファイル
- `test-fixtures/` 画像処理・実資料テスト用の置き場

添付された原本は変更せず、開発フォルダには控えを配置しています。

## 導入・確認済み

| 項目 | 状態 | 確認内容 |
|---|---|---|
| Android Studio | PASS | `C:\Program Files\Android\Android Studio\bin\studio64.exe`、ProductVersion `262.9437.185.0-AI` |
| JDK | PASS | Microsoft OpenJDK 17.0.14、`C:\Android\Jdk` |
| Android SDK | PASS | `C:\Android\Sdk` |
| SDK Platform | PASS | Android API 36 rev. 2 |
| Build Tools | PASS | 36.0.0 |
| Platform Tools / ADB | PASS | 36.0.0、ADB 1.0.41 |
| Android Emulator | PASS | 37.1.11 |
| API 36 system image | PASS | default / x86_64 |
| Android Virtual Device | PASS | 既存のAPI 36 AVDを確認。既存設定は変更していない |

## ユーザー環境変数

次の値をユーザー範囲に設定しました。新しく開いたターミナルやAndroid Studioから有効になります。

```text
JAVA_HOME=C:\Android\Jdk
ANDROID_HOME=C:\Android\Sdk
ANDROID_SDK_ROOT=C:\Android\Sdk
```

ユーザーPATHには、次の3項目を既存項目を保持したまま追加しました。

```text
C:\Android\Jdk\bin
C:\Android\Sdk\platform-tools
C:\Android\Sdk\cmdline-tools\latest\bin
```

## 設計に基づく導入判断

初期構成では次を導入対象とします。

- Kotlin
- Jetpack Compose
- Navigation Compose
- Lifecycle ViewModel / StateFlow
- Kotlin Coroutines
- Android Photo Picker / Activity Result API
- AndroidX ExifInterface
- Android標準 `PdfDocument`
- Android Storage Access Framework
- AndroidX FileProvider

OpenCVとCameraXは後続のVer.0.3以降で使用するため、ソースプロジェクト作成時に追加します。C++/NDK、Hilt/Dagger/Koin、Room、Retrofit、WorkManager、独自PDFライブラリは、設計書どおり現時点では導入しません。

## 注意事項

SDK Managerは、`cmdline-tools;19.0` が既存の `latest` フォルダに配置されているため、場所に関する警告を表示します。SDKコマンド自体は実行でき、既存のWorkout Tracker環境と共有されているため、今回の環境構築では移動・再配置していません。

Gradleはシステム全体へ固定インストールしていません。PaperScanのAndroidプロジェクト作成時にGradle Wrapperを使用し、プロジェクト単位でバージョンを固定します。

## 検証レベル

- 環境ファイル・実行ファイル確認: PASS
- Android Studio GUIの初回設定: NOT RUN
- PaperScanプロジェクトのビルド: NOT RUN
- EmulatorでのPaperScan動作: NOT RUN
- Android実機確認: NOT RUN

次の実装作業は、詳細設計 v0.1.1 の `Issue 1: Android project skeleton + navigation` から開始します。
