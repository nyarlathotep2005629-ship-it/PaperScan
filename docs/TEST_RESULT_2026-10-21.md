# PaperScan テスト実行結果 2026-10-21

## 判定

| Check | Status | Evidence |
|---|---|---|
| `testDebugUnitTest` | PASS | 18/18（4 test class、失敗0、Error0、Skipped0） |
| `lintDebug` | PASS | Gradle `BUILD SUCCESSFUL`。既存のdeprecated warningのみ |
| `assembleDebug` | PASS | Debug APK生成 |
| `assembleDebugAndroidTest` | PASS | 既存10件 + 今回追加3件を含むInstrumentation APKコンパイル |
| Instrumentation test実行 | NOT RUN | `emulator-5562` が `offline`。ADB `device offline` |
| 実機カメラ / 実画像品質 | NOT RUN | 実端末未接続 |
| Import→Session→PDF→SAF実機統合 | NOT RUN | OS Picker/SAF実行環境なし |

## 実行コマンド

PaperScanの隔離コピーで次を実行した。

```powershell
cmd.exe /d /c gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-daemon
```

初回は追加テストの `ScanSession` 位置引数の記述ミスでAndroidTestコンパイルが止まった。テストコードを名前付き引数へ修正し、同じコマンドを再実行して全タスクが成功した。製品本番コードは変更していない。

## 今回追加したテスト

- `PhotoImportServiceInstrumentedTest.importUris_deduplicatesFileUris_andDoesNotModifyOriginalBytes`
- `SessionStoreRecoveryInstrumentedTest.load_corruptedSession_returnsCorruptedWithoutThrowing`
- `PdfExporterInstrumentedTest.export_invalidSource_returnsFailure_andDoesNotLeavePartialPdf`

これらはInstrumentation APKへコンパイルされているが、端末offlineのため実行結果は `NOT RUN` である。コンパイル成功をテスト成功へ変換しない。

## 残存未検証

- Photo Pickerの実OS UI、キャンセル、persistable permission、fallback copy失敗
- PDF保存のSAF成功・失敗・キャンセル・再試行
- セッション保存途中の異常終了、再起動、`session.json.tmp` / 正本のRecovery
- Import → Session → PDF → SAFの実端末統合
- CameraX実機撮影、実授業資料、複数端末での性能・メモリ
- JpegPdfWriterのviewer互換性レビュー（現行経路の確認範囲外）

これらの不足と、テストのために本番コードを大きく改造しない理由は [`TEST_GAP_MATRIX_2026-10-21.md`](TEST_GAP_MATRIX_2026-10-21.md) に記録した。

## CI判断

GitHub Actions workflowは現行リポジトリに存在しない。Unit/Lint/Debug buildは軽量CI候補だが、Android SDK・OpenCV・Gradle依存をrunnerへ固定してから導入するのが安全である。Instrumentation、実機カメラ、SAF、実画像品質はCIの軽量ゲートへ混ぜず、接続端末を用いる別ゲートとする。今回はworkflowを追加していない。
