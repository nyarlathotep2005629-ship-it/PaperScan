# PaperScan テスト不足・対応状況 2026-10-21

## 判定の前提

対象はGitHub `main` の現行ソースと既存テストである。既存のテスト仕様書が定めるIDを尊重し、実行していない項目をPASSへ変換しない。Android OSのPicker/SAF、実機カメラ、異常終了後の再起動は、Unit Testの結果から推論しない。

## 対応表

| 優先度 | 要求 / テスト範囲 | 現行実装の確認 | 既存テスト | 今回の対応 | 現在の判定 |
|---|---|---|---|---|---|
| P0 | Photo Picker / Import、複数URI、重複 | `PhotoImportService` と `PickVisualMedia` を確認 | 既存テストなし | `PhotoImportServiceInstrumentedTest` を追加 | 実機/Instrumentation実行まではNOT RUN |
| P0 | 元画像非破壊 | Importはfallbackへコピーする実装 | 明示テストなし | Import元バイト列不変テストを追加 | 実機/Instrumentation実行まではNOT RUN |
| P0 | PDF成功 | `PdfExporter`、A4、順序、PdfRenderer確認 | 既存1件、stress 1件 | 変更なし | 現行記録では過去Instrumentation結果を再実行していない |
| P0 | PDF失敗 | `ExportResult.Failure` と出力削除を確認 | 明示テストなし | 不正画像で失敗・未完成PDFなしを追加 | 実行まではNOT RUN |
| P0 | PDFキャンセル | UIはOSの保存先選択をキャンセルして `onSaveCancelled()` | 専用テストなし | OSダイアログを伴うため追加保留 | NOT RUN |
| P0 | SAF保存成功/失敗/再試行 | `savePdfTo`、失敗時にSession/PDFを保持 | 専用テストなし | Context/ContentResolverのテスト差し替え境界なし | NOT RUN |
| P0 | Session復元 | `SessionStore.load` とViewModel初期化 | round-trip 1件 | 破損JSONの `Corrupted` 分類を追加 | 実行まではNOT RUN |
| P0 | Recovery / 異常終了 | tmp/正本保持、stale cache cleanup実装 | 専用の異常終了テストなし | 本番コード変更なし。端末再起動系は保留 | NOT RUN |
| P1 | Import→Session→PDF→SAF | UI/ViewModelに実装経路あり | 個別部品のみ | 本番変更なし。依存差し替え境界なしのため統合テストは保留 | NOT RUN |
| P1 | Session操作/ViewModel | `ScanSessionViewModel` に実装あり | UIは一部のみ | 新規テストを無理に追加せず不足を記録 | NOT RUN |
| P1 | 実機カメラ/品質/性能 | CameraX/OpenCVの実装あり | fixture中心 | 実機未接続 | NOT RUN |

## 追加テストの意図

- `PhotoImportServiceInstrumentedTest`: Photo PickerのOS UIを再現せず、アプリ側Import契約（重複排除、fallback、元画像非破壊）を確認する。
- `SessionStoreRecoveryInstrumentedTest`: 破損した正本JSONをクラッシュさせず `Corrupted` として返す契約を確認する。
- `PdfExporterInstrumentedTest.export_invalidSource_returnsFailure_andDoesNotLeavePartialPdf`: 画像デコード失敗を成功扱いにせず、一時PDFを残さない契約を確認する。

## 追加実装を保留した理由

SAF保存の成功・失敗・キャンセルと再試行、ViewModelのCoroutine状態、異常終了・再起動、ImportからSAFまでの一連のUI経路は、現状のコンストラクタがAndroid `Context` / `ContentResolver` / `viewModelScope`へ直接結合している。テストのためだけに本番コードを大きく改造すると、今回の「既存実装を尊重し、必要最小限」という範囲を超える。次の段階では、まず保存・Import・時刻・Dispatcherを小さなインターフェースへ切り出す設計レビューを行い、その承認後にViewModelテストを追加する。
