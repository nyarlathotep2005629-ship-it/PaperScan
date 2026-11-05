# PaperScan vNext 検証・リリース判定

更新日: 2026-11-05

## 現在の判定

- Debug / unsigned Release build: PASS
- 自動テスト: 152件PASS（Unit 118、Compose UI 6、Instrumented integration 28）
- `lintDebug`: エラー0、警告20
- OpenCV独立ground truth: 合成fixture 1枚でIoU 0.9961、平均四隅誤差0.6107 px
- 検索可能PDF: PDFBox抽出とAndroid `PdfRenderer`の不可視文字層を確認。外部ビューア互換性はNOT RUN
- **vNext全体のRelease受入: NOT ACCEPTED**。実機カメラ、外部PDF viewer、実文書OCR・品質、物理端末性能、署名済みReleaseは未確認。

## 詳細資料

- [テスト仕様・要件トレーサビリティ](testing/TEST_SPECIFICATION.md)
- [152ケースのテストケースマトリクス](testing/TEST_CASE_MATRIX.csv)
- [実行結果・品質評価・残存リスク](testing/TEST_RESULTS.md)

Debug/Release build成功は、実機受入や署名済み配布物の完成とは区別する。実施日時、環境、数値の条件は実行結果資料を参照。
