# PaperScan vNext 現行監査

この監査の数値とPDF方式は2026-11-05時点。今回の変更と最新の判定は[2026-11-12 継続開発・検証記録](testing/2026-11-12_CONTINUATION.md)を参照。

監査・更新日: 2026-11-05
対象: `codex/paperscan-vnext-foundation` の作業ツリー
比較基準: 作業開始時の `02c352b`（`main` / `origin/main`）

## Gitと配布物

- `main`へ直接変更せず、`codex/paperscan-vnext-foundation`で作業している。第2次開発の開始HEADは`90ae285363600c178f79a6577b8c0dbeb9c599cc`。この作業のコミット・push・PR状態は完了報告に記録する。
- `APK/` には作業開始前から187,932,275 bytesのAPKと記録ファイルがあり、今回の変更・コミット対象に含めない。
- Universal Debug APKは`app/build/outputs/apk/debug/app-debug.apk`、235,148,895 bytes。unsigned Release APKは`app/build/outputs/apk/release/app-release-unsigned.apk`、225,756,915 bytes。AAB、ABI別インストールサイズ、署名済み配布物は未確認。
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
| CameraX | 手動シャッター、`ImageAnalysis`、`Preview`と共有ViewPortのUseCaseGroup、KEEP_ONLY_LATEST、250 ms解析間隔、複数フレーム安定化、`PreviewView`座標への枠表示を実装。フレームはfinallyでcloseする。 | 実機のカメラ初期化、権限・ライフサイクル、実カメラフレームから表示枠までの座標整合と連続撮影は未確認。AVDには物理カメラがないため実カメラE2Eではない。 |
| 書類検出 | OpenCVのCanny、輪郭近似、四角形形状、境界支持、画像外枠抑制を使用。低信頼結果は自動確定せず、手動四隅を維持する。独立アノテーション付き合成fixtureで平均角誤差0.6107 px、IoU 0.9961を測定。 | ground truthは1枚のみ。旧版対新版比較は検出アルゴリズム変更がないため未実施。検出率、誤検知・未検出率、信頼度別正解率、実画像・検出時間は未確認。 |
| 補正 | 透視変換、AUTOの制限付き色バランス、TEXTの軽度CLAHE、COLORの低強度コントラストを追加。元画像は上書きしない。編集画面で元画像／補正後を切り替える。 | 影・照明むら・反射を個別に推定・補正する処理は未実装。細線・数式・色の画質はfixtureによる表示確認までで、画質指標比較は未実施。 |
| ページ管理 | 追加・削除・並べ替えを維持。複数選択、全選択・解除、AUTO / TEXT / COLOR一括適用を追加。一括変更で手動四隅を保持し、処理モード変更時は該当OCRを無効化する。 | 一括の影補正スライダーや一括補正前後比較は未実装。 |
| OCR | 編集確定後の非同期OCR、再試行、コピー、ページ位置、revisionキャッシュ、schema保存、ページごとのAUTO/LATIN/CHINESE/JAPANESE/KOREAN言語選択を実装。4モデルは端末内処理。信頼度0.20未満を除外し、IoU 0.85以上の重なり候補は先に処理した認識器の結果を残す。 | 簡体字合成fixtureのCERは0.50で誤認識を継続記録。実文書・混在比率別・手書き・数式・表の精度は未確認。複数認識器の順序優先はヒューリスティックで、網羅的な混在品質評価ではない。 |
| 検索可能PDF | 画像PDF／OCR文字層付きPDF、SAF保存、画質・用紙・向き・色の選択を実装。複数ページCJK・補助平面UnicodeのPDFBox抽出、ToUnicode、Android `PdfRenderer`の不可視文字層をテスト。OCR失敗ページは空の文字層で画像を出力する。 | PDFBoxでの抽出はPASSだが、PDFビューア上での検索・選択・コピー、位置合わせ、viewer間互換性は手動未確認。日本語CIDフォントを埋め込んでいないため、各viewerでの挙動は未確定。 |
| UI | 主要なカメラ遷移、撮影ボタン、ページ選択・一括変更、編集操作、OCR言語選択、PDF設定をCompose UI Test 6件で確認。 | 画面全体、テーマ、文字拡大、画面サイズ、アクセシビリティを網羅していない。実端末での確認も未実施。 |
| セッション | schema v2へ拡張。schema v1の読み込み移行、OCRの保存・復元、同時保存、未知schema、不正JSONを自動テスト。保存をMutexで直列化し、失敗時に一時ファイルを削除する。 | 実ユーザーの既存セッション移行、ディスク容量不足、プロセス強制終了・電源断は未実施。 |

## 画質プリセットの初回測定

API 36 x86_64エミュレータ上で、3000×4242 pxの合成A4相当ページ（細線グリッド・色付き領域・文字）を3設定でPDF化した。生成PDF全体のサイズは次の通り。

| 設定 | ラスター上限 | JPEG品質 | 実測PDF容量 |
|---|---:|---:|---:|
| 高画質 | 300 DPI | 92 | 330,602 bytes |
| 標準 | 200 DPI | 85 | 178,679 bytes |
| 軽量 | 120 DPI | 70 | 81,964 bytes |

これは1つの合成ページでの容量比較であり、実スキャンの代表容量や視覚品質の優劣を確定する測定ではない。ユーザー文書のページ数別容量・細線保持の追加評価が必要。

## 検証環境

- Unit 118件、Instrumented 34件（うちCompose UI 6件）、合計152件を実行し全件PASS。前回61件から91件増加。
- `lintDebug`はエラー0・警告20。`assembleDebug`と`assembleRelease`はPASS。Release APKはunsigned。
- Instrumented/UI TestはAndroid API 36 x86_64エミュレータ1台で実行した。ADB上の別エミュレータ`emulator-5562`はofflineだったため対象外。
- 物理端末、実カメラ、代表的PDFビューアアプリ、ストア配布用Releaseは未確認。
- 実行結果と残項目は[第2次開発テスト結果](testing/TEST_RESULTS.md)に記録する。
