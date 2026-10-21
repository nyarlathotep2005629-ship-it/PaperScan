# PaperScan テスト実行結果 2026-10-21

## 判定

| Check | Status | Evidence |
|---|---|---|
| `testDebugUnitTest` | PASS | 18/18（4 test class、失敗0、Error0、Skipped0） |
| `lintDebug` | PASS | Gradle `BUILD SUCCESSFUL`。既存のdeprecated warningのみ |
| `assembleDebug` | PASS | Debug APK生成 |
| `assembleDebugAndroidTest` | PASS | 既存10件 + 今回追加3件、合計13件を含むInstrumentation APKコンパイル |
| Instrumentation test実行 | NOT RUN | `emulator-5562` が `offline`。ADB `device offline` |
| 実機カメラ / 実画像品質 | NOT RUN | 実端末未接続 |
| Import→Session→PDF→SAF実機統合 | NOT RUN | OS Picker/SAF実行環境なし |

## 実行コマンド

PaperScanの隔離コピーで次を実行した。

```powershell
cmd.exe /d /c gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-daemon
```

- 実行環境: Windows 10.0.26200 x64、Android SDK `C:\Android\Sdk`、Gradle 9.4.1
- レビュー再確認: 2026-10-21 21:29:13.228–21:29:30.670 JST、`BUILD SUCCESSFUL`、exit 0
- Unit XML: 2026-10-21 21:23:24 JST、18 tests、failure 0、error 0、skipped 0
- ADB確認: 2026-10-21 21:29:30.672 JST、`emulator-5562 offline`

初回は追加テストの `ScanSession` 位置引数の記述ミスでAndroidTestコンパイルが止まった。テストコードを名前付き引数へ修正し、同じコマンドを再実行して全タスクが成功した。

レビューで `duplicateCount` の期待値と実装の不一致を確認した。契約を「今回渡されたURIのうち、入力内重複または既存セッションとの重複により追加されなかった選択数」とし、`uris.size - newUris.size` で数える最小限の本番コード変更を行った。Import成功/失敗数、fallback copy、Session/PDF/SAFの処理は変更していない。

## 今回追加したテスト

- `PhotoImportServiceInstrumentedTest.importUris_countsInputAndExistingDuplicates_andDoesNotModifyOriginalBytes`
- `SessionStoreRecoveryInstrumentedTest.load_corruptedSession_returnsCorruptedWithoutThrowing`
- `PdfExporterInstrumentedTest.export_invalidSource_returnsFailure_andDoesNotLeavePartialPdf`

PhotoImportテストは、同じURIを1入力内へ2回渡した場合と、既存セッションに同じURIがある場合を別々に検証し、どちらも `duplicateCount=1` であることを要求する。元画像バイト列不変も両経路で確認する。

これらはInstrumentation APKへコンパイルされているが、`adb devices -l` で `emulator-5562 offline` のため実行結果は `NOT RUN` である。コンパイル成功をテスト成功へ変換しない。

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
