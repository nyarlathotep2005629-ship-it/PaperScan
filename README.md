# PaperScan

Android向けローカル完結型ドキュメントスキャナーです。現在はvNext開発ブランチで、アプリの`versionName`は`0.1.0`です。ストア配布用Releaseとしては未検証です。

## 実装済み機能

- CameraX手動撮影、複数ページの追加、直近ページ表示、ページ一覧への移動
- Photo Pickerからの画像追加
- OpenCVによる紙面候補検出、手動四隅編集、90度回転、元画像／補正後プレビュー
- AUTO / TEXT / COLORのページ単位補正と、選択ページへの一括モード適用
- ML Kit bundled recognizerによる英語・日本語・中国語・韓国語の端末内OCR、文字コピー
- 画像PDF／検索可能PDF、OCR初期値ON、画質・用紙・向き・色の選択、Android SAF保存
- JSONセッション復元、schema v1からv2への移行
- ライト／ダーク／システム連動テーマと設定保持

## 検証済みの範囲と制約

- API 36 x86_64エミュレータでUnit、Instrumented、UI、PDF抽出テストを実行しています。実機・実カメラは未確認です。
- 検索可能PDFはToUnicode付き文字層のPDF抽出とAndroid `PdfRenderer`での不可視描画を確認しています。代表的なPDFビューアでの検索・選択・コピーは未確認です。
- 簡体字中国語の合成fixtureで誤認識があり、OCR精度は保証しません。手書き・数式・複雑な表は別途評価が必要です。
- CameraXのリアルタイム検出枠、影・反射専用補正、定量的な検知精度比較は未実装／未検証です。
- bundled OCRモデルによりUniversal Debug APKは開始時点のAPKより約56.6 MiB大きくなりました。詳細とRelease前の残項目は[`docs/vnext/`](docs/vnext/)を参照してください。

## 技術

- Kotlin / Jetpack Compose / Material 3
- CameraX 1.6.2
- OpenCV 4.13.0
- ML Kit Text Recognition v2 16.0.1（bundled）
- PaperScan内の逐次JPEG PDF writer

要件、監査、アーキテクチャ、検証結果は[`docs/vnext/`](docs/vnext/)に記録しています。
