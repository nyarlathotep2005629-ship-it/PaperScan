# PaperScan

Android向けローカル完結型ドキュメントスキャナです。

## 主な機能

- CameraXによる撮影
- Photo Pickerから画像追加
- OpenCVによる自動紙面検出
- 手動四隅補正
- Perspective補正
- AUTO / TEXT / COLOR画質モード
- ページ追加・削除・並べ替え
- Session復元
- 複数ページPDF生成
- SAFによるローカル保存

## 技術

- Kotlin
- Jetpack Compose
- CameraX 1.6.2
- OpenCV 4.13.0
- Android PdfDocument

## Status

`v1.0 Candidate`

`Physical-device acceptance test pending`

要件定義、基本設計、詳細設計、テスト仕様および環境記録は [docs/](docs/) を参照してください。
