# PaperScan vNext 検証・リリース判定

更新日: 2026-11-12

## 現在の判定

- Debug / unsigned Release build: PASS
- 自動テスト: 158件PASS（Unit 118、Compose UI 6、Instrumented integration 34）。API 36 AVDで実施
- `lintDebug`: エラー0、警告20
- OpenCV独立ground truth: 合成陽性10/10検出・陰性0/1誤検知、平均IoU 0.9958、平均四隅誤差0.7264 px。同じ紙形状を使った合成資料のみ
- 検索可能PDF: Type 3不可視フォントへ変更。PDFBox抽出、Android `PdfRenderer`、pdfplumber抽出、Popplerレンダリングを確認。GUIビューアでの検索・選択・コピーはNOT RUN
- **vNext全体のRelease受入: NOT ACCEPTED**。実機だけでなく、実画像検知、補正・OCR品質、PDF GUI操作、競合・障害注入、アクセシビリティ、署名済みReleaseが残る。

## 詳細資料

- [テスト仕様・要件トレーサビリティ](testing/TEST_SPECIFICATION.md)
- [152ケースのテストケースマトリクス](testing/TEST_CASE_MATRIX.csv)
- [実行結果・品質評価・残存リスク](testing/TEST_RESULTS.md)
- [2026-11-12 継続開発・検証記録](testing/2026-11-12_CONTINUATION.md)
- [物理Android端末の受入チェックリスト](DEVICE_ACCEPTANCE_CHECKLIST.md)

Debug/Release build成功は、実機受入や署名済み配布物の完成とは区別する。実施日時、環境、数値の条件は実行結果資料を参照。
