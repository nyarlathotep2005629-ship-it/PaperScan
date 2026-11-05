# PaperScan vNext 現行監査

監査・更新日: 2026-11-05
対象: `codex/paperscan-vnext-foundation` の作業ツリー
比較基準: 作業開始時の `02c352b`（`main` / `origin/main`）

## Gitと配布物

- `main`へ直接変更せず、作業ブランチで進めている。作業開始時点からあったOpenCV、カメラ、四隅編集、PDF、テストの変更を保持した。
- `APK/` には作業開始前から187,932,275 bytesのAPKと記録ファイルがあり、今回の変更・コミット対象に含めない。
- 今回生成したDebug APKは `app/build/outputs/apk/debug/app-debug.apk`。Universal Debug APKのサイズは247,264,671 bytesで、開始時点のDebug APKより59,332,396 bytes（31.57%）増加した。主な追加要因は4言語のbundled OCRモデル。AAB、ABI別インストールサイズ、Release APKサイズは未計測。
- リポジトリは公開設定。今回の配布に個人スキャン画像・OCRログ・鍵・署名情報を追加しない。既存の `APK/` はステージしない。

## 構成・設定

| 項目 | 作業ツリーで確認した値 |
|---|---|
| UI・言語 | Kotlin、Jetpack Compose / Material 3 |
| カメラ・画像 | CameraX 1.6.2、OpenCV 4.13.0、ExifInterface 1.3.7 |
| Android | `applicationId=com.paperscan`、`minSdk=29`、`targetSdk=36` |
| Gradle / AGP / Kotlin | Wrapper 9.4.1 / AGP 9.2.0 / Kotlin 2.2.10 |
| OCR | ML Kit Text Recognition v2 16.0.1。Latin、Chinese、Japanese、Korean各bundled recognizer |
| PDF | アプリ内逐次JPEG PDF writer。PDFBox-Android 2.0.27.0はPDF抽出のInstrumented Test専用 |
| Manifest権限 | CAMERAのみ（Android生成のdynamic receiver権限を除く）。INTERNET / ACCESS_NETWORK_STATEはマージ後Manifestから除外 |
| Gradle versionName | `0.1.0`。READMEの旧`v1.0 Candidate`表記とは異なるため、Release扱いにしない |

## 機能監査と今回の状態

| 領域 | 実装・確認できた状態 | 未完了・未確認 |
|---|---|---|
| CameraX | 手動シャッター後にカメラ画面を保ち、ページ数・直近サムネイル・ページ一覧操作を表示する。重複撮影を抑止する処理を追加。 | `ImageAnalysis`によるライブ検出、プレビュー枠、複数フレーム安定化、撮影フレームとの座標照合は未実装。実機での連続撮影・失敗再試行は未確認。 |
| 書類検出 | OpenCVのCanny、輪郭近似、四角形形状、境界支持、画像外枠抑制を使用。低信頼結果は自動確定せず、手動四隅を維持する。テスト用fixtureで候補を確認。 | 正解四隅のアノテーションがなく、旧版対新版の平均角誤差・成功率・誤検知率・時間比較は未実施。高精度化の定量主張はできない。 |
| 補正 | 透視変換、AUTOの制限付き色バランス、TEXTの軽度CLAHE、COLORの低強度コントラストを追加。元画像は上書きしない。編集画面で元画像／補正後を切り替える。 | 影・照明むら・反射を個別に推定・補正する処理は未実装。細線・数式・色の画質はfixtureによる表示確認までで、画質指標比較は未実施。 |
| ページ管理 | 追加・削除・並べ替えを維持。複数選択、全選択・解除、AUTO / TEXT / COLOR一括適用を追加。一括変更で手動四隅を保持し、処理モード変更時は該当OCRを無効化する。 | 一括の影補正スライダーや一括補正前後比較は未実装。 |
| OCR | 編集確定後の非同期OCR、再試行、コピー、ページ位置、revisionキャッシュ、schema保存を追加。4モデルは端末内処理。信頼度0.20未満を除外し、IoU 0.85以上の重なり候補は先に処理した認識器の結果を残す。 | 簡体字の合成fixtureで複数文字誤認識を確認。手書き・数式・表の精度、実文書精度、認識言語の手動指定は未検証／未実装。複数認識器の順序優先はヒューリスティックで、網羅的な混在品質評価ではない。 |
| 検索可能PDF | 画像PDF／OCR文字層付きPDF、SAF保存、画質・用紙・向き・色の選択を実装。ToUnicode抽出とAndroid `PdfRenderer`で文字非表示をテスト。OCR失敗ページは空の文字層で画像を出力する。 | PDFBoxでの抽出はPASSだが、PDFビューア上での検索・選択・コピー、位置合わせ、viewer間互換性は手動未確認。日本語CIDフォントを埋め込んでいないため、各viewerでの挙動は未確定。 |
| UIテーマ | 初期値システム連動、ライト／ダーク選択、SharedPreferencesで再起動後に保持。 | 実端末の画面サイズ・拡大率・アクセシビリティ確認は未実施。 |
| セッション | schema v2へ拡張。schema v1の読み込み移行、OCRの保存・復元を自動テスト。未知の将来schemaを拒否する。 | 実ユーザーの既存セッションを使った移行は未実施。 |

## 画質プリセットの初回測定

API 36 x86_64エミュレータ上で、3000×4242 pxの合成A4相当ページ（細線グリッド・色付き領域・文字）を3設定でPDF化した。生成PDF全体のサイズは次の通り。

| 設定 | ラスター上限 | JPEG品質 | 実測PDF容量 |
|---|---:|---:|---:|
| 高画質 | 300 DPI | 92 | 330,602 bytes |
| 標準 | 200 DPI | 85 | 178,679 bytes |
| 軽量 | 120 DPI | 70 | 81,964 bytes |

これは1つの合成ページでの容量比較であり、実スキャンの代表容量や視覚品質の優劣を確定する測定ではない。ユーザー文書のページ数別容量・細線保持の追加評価が必要。

## 検証環境

- Instrumented/UI TestはAndroid API 36 x86_64エミュレータ1台で実行した。ADB上の別エミュレータ`emulator-5562`はofflineだったため対象外。
- 物理端末、実カメラ、代表的PDFビューアアプリ、ストア配布用Releaseは未確認。
- 実行結果と残項目は[検証・リリース計画](TEST_AND_RELEASE.md)に記録する。
