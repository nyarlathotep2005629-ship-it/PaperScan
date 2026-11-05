# PaperScan vNext テスト仕様・トレーサビリティ

更新日: 2026-11-05

対象: 第2次開発作業ツリー（開始時コミット `90ae285363600c178f79a6577b8c0dbeb9c599cc`）
実行環境: Windows / Gradle Wrapper 9.4.1 / Android API 36 x86_64 AVD

## 方針

要件からテスト、テストコード、実行結果を追えるようにする。テストは既存61件を基準として、今回追加・拡張した検証も含め、実行可能な自動テスト全件を[テストケースマトリクス](TEST_CASE_MATRIX.csv)へ記録した。マトリクスは152ケースで、各行に要件ID、対象機能、テストレベル、分類、入力条件、期待結果、実行方法、結果、テストコード位置を持つ。

入力データと具体的な期待値は、根拠欄に示すテストメソッド内のArrange / Assertを正本とする。表の入力・期待列はケース名を短く要約し、テストコードにある数値、fixture、assertionを再掲して内容が二重管理されないようにしている。

## テストID

マトリクスのIDは `UT-`（Unit）、`IT-`（Instrumented integration）、`UI-`（Compose UI）と、テストコード相対パス・メソッド名を連結した値のSHA-256先頭8桁で構成する。ファイル内の `TC_*` 文字列は既存・新規テストの補助タグとして保持し、クラスをまたぐ重複があるためマトリクスの一意IDには使わない。IDはテストの移動・改名時に変わるので、その場合はマトリクスを更新する。

## テストレベルと実行環境

| レベル | ケース数 | 実行方法 | 主な対象 |
|---|---:|---|---|
| Unit | 118 | `testDebugUnitTest` | 幾何、座標変換、OCR revision・結果形式、セッション操作、PDF寸法・ラスター境界、カメラ検知安定化・間引き・フレーム終了 |
| Compose UI | 6 | API 36 AVD上の`connectedDebugAndroidTest` | ホームからのカメラ遷移、撮影操作、選択・一括処理、編集、OCR言語選択、PDF出力設定 |
| Instrumented integration | 28 | API 36 AVD上の`connectedDebugAndroidTest` | OpenCV、ML Kit、PDFBox解析、PDF出力、セッションJSON・一時ファイル |
| **合計** | **152** |  |  |

AVD: `PaperScan_API36(AVD) - 16`（Android API 36、x86_64）。ADB上の`emulator-5562`はofflineで、Gradleはこれを対象外にした。実機カメラの結合試験はこのAVD試験に含まない。

## 要件との対応範囲

要件IDの本文は[要件書](../REQUIREMENTS.md)を参照する。件数はマトリクスの主な対応先で集計し、1ケースが複数要件IDに対応する場合がある。

| 要件ID | 主なテスト数 | 検証範囲 |
|---|---:|---|
| REQ-CAM-01 | 2 | カメラ操作への遷移、撮影ボタンの既存ページ数への非干渉 |
| REQ-CAM-02 | 17 | 3フレーム安定化、揺れ、見失い、低信頼度、急な位置変化、間引き境界、例外時も含むフレームclose |
| REQ-DETECT-01 / REQ-PROC-01 | 8 | 代表OpenCV fixture、空画像、回転、補正モード、独立ground truthの1例 |
| REQ-GEO-01 | 39 | 四隅の向き・交差・凹形状・範囲・微小値、射影寸法、表示レターボックス、手動編集 |
| REQ-OCR-01 / REQ-OCR-02 | 18 | revision無効化、言語指定、結果・bounds検証、多言語合成fixture、簡体字CER回帰 |
| REQ-OCR-01 / REQ-GEO-01 | 2 | 編集画面でのOCR言語選択とページ状態更新 |
| REQ-PAGE-01 | 22 | 追加・削除・順序・選択・一括画質変更と対象外ページ保持 |
| REQ-PDF-01 | 2 | CJK・補助平面文字・複数ページの抽出、ToUnicodeと不可視文字層 |
| REQ-PDF-02 | 35 | 用紙・方向・品質・色、寸法とラスター境界、1/10/20/30ページの出力 |
| REQ-SESSION-01 | 7 | schema v1移行、round-trip、未知schema、不正JSON、同時保存、失敗時の既存データ保持 |

この対応はテスト実装への追跡性を示す。全ての要件条件を受け入れ済みという意味ではない。未検証条件は[実行結果・品質評価](TEST_RESULTS.md)に記載する。

## Fixture一覧

| 区分 | Fixture | 用途・制約 |
|---|---|---|
| detection | `app/src/androidTest/assets/fixtures/`内の`perspective_a4.png`、`overhead_a4.png`、`dark_document.png`、`white_table.png`、`wood_table.png`、`shadow.png`、`image_edge.png`、`no_paper.png`等 | 小型の合成・決定的画像。一般の実文書の性能を代表しない。 |
| detection ground truth | `app/src/androidTest/assets/fixtures/detection_ground_truth.json` | `perspective_a4.png` 1枚のTL/TR/BR/BL独立アノテーション。検出器出力から生成していない。誤検知率や見逃し率を推定するには不十分。 |
| correction | `circuit_lines.png`、`math_thin_lines.png`、`colored_document.png` | 細線・数式・色の処理出力を確認。PSNR/SSIMや細線保持率による比較 fixture ではない。 |
| OCR | Instrumented Testが生成する英語・日本語・簡体字・繁体字・韓国語の合成文字画像 | 言語選択と基本認識の技術確認。実文書、手書き、縦書き、低解像度の精度評価ではない。 |
| PDF / session | テスト内で生成するBitmap、OCR文字列、schema v1 JSON | 外部資料・実ユーザーデータを使わず、抽出、出力設定、保存移行を確認する。 |

fixtureは12ファイル（JSON正解データ1、PNG 11）で、個人情報を含む画像は含まない。

## 合否基準と実行コマンド

- 自動テストは実行時点のGradle XMLで失敗・エラー・skipが0件ならPASSとする。
- 検知の独立正解データ試験はIoU `>= 0.80`、平均四隅誤差が画像長辺の5%未満をこの単一fixture用の回帰しきい値とする。製品精度の目標値ではない。
- 簡体字OCR回帰は全6文字を期待文字列に残し、CER `<= 0.50`を既知誤認識を隠さない暫定しきい値としている。これは多言語の合格品質を意味しない。
- PDFBoxでの抽出・Android `PdfRenderer`での不可視表示はPDF内部の技術試験。外部viewerでの検索・選択・コピーは別の手動受入とする。
- Lint警告、ビルド結果、実機・外部互換性の状態はテスト件数へ足し合わせない。

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
.\gradlew.bat connectedDebugAndroidTest
```

実測値、コマンド別の結果、NOT RUN、残存リスクは[実行結果・品質評価](TEST_RESULTS.md)を参照。
