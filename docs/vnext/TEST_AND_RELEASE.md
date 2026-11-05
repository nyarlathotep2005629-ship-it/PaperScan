# PaperScan vNext 検証・リリース計画

更新日: 2026-11-05
実行環境: Windows / Gradle Wrapper 9.4.1 / Android API 36 x86_64 AVD（`PaperScan_API36(AVD) - 16`）

## 今回の自動検証

| Check | 結果 | Evidence |
|---|---|---|
| `testDebugUnitTest` | PASS | 36件、失敗0、エラー0。幾何、四隅編集、セッション操作、PDFサイズ計算を含む。 |
| `compileDebugAndroidTestKotlin` | PASS | Instrumented/UI Testコードをコンパイル。 |
| `assembleDebug` | PASS | Debug APK生成。`app/build/outputs/apk/debug/app-debug.apk`、247,264,671 bytes。Release APKではない。 |
| `lintDebug` | PASS | 0 errors、19 warnings。既存Android/依存バージョン、アイコン、KTX提案等。 |
| `connectedDebugAndroidTest` | PASS | 25件、失敗0、skip 0。PDF、PDF設定、30ページ負荷、OpenCV、セッション移行、UI、OCRを実行。offlineの`emulator-5562`はGradleが対象外とし、API36 AVDで実行。 |
| `git diff --check` | PASS | whitespace errorなし。Gitは既存ファイルのLF→CRLF変換予定を通知したが、差分検査は成功。 |
| merged Manifest権限確認 | PASS | CAMERAとAndroid生成のdynamic receiver権限。INTERNET / ACCESS_NETWORK_STATEなし。 |

PDFBox-Androidはテスト依存だけで、PDFBoxによる出力自体は行わない。混在OCRやPDF viewerの手動操作を自動PASSへ読み替えていない。

## OCR合成fixture結果

各テスト画像はエミュレータ上の合成文字。処理時間は当該実行の単発値で、実機性能や文書OCR速度を示さない。

| ケース | 期待文字 | エミュレータ認識結果 | 時間 | 判定 |
|---|---|---|---:|---|
| English | `PaperScan` | `PaperScan` | 147 ms | PASS |
| Chinese simplified | `简体中文测试` | `筒休中文測试` | 131 ms | 部分一致。誤認識あり |
| Chinese traditional | `繁體中文測試` | `繁體中文測試` | 222 ms | 合成画像で一致 |
| Japanese | `日本語テスト` | `日本語テスト` | 148 ms | 合成画像で一致 |
| Korean | `한국어` | `한국어` | 100 ms | 合成画像で一致 |
| 混在サービス | 4行 | 重複抑制後4行、簡体字を含む中国語行に誤認識あり | 2,444 ms | 言語token・正規化bounds・行重複上限テストPASS。全文一致ではない |

簡体字ケースはテストを通すために全文一致を要求していない。上記をOCR精度保証として扱わず、実文書、複数解像度、言語比率別データセットの評価を追加する。

## PDF・出力設定の証拠

- `SearchablePdfExporterInstrumentedTest`: 1ページで英語、日本語、簡体字・繁体字中国語、韓国語を含む文字列を出力し、PDFBox `PDFTextStripper`で抽出全文とToUnicodeを確認。Android `PdfRenderer`で文字レイヤーが視覚表示されないことを確認。**外部viewerの検索・選択・コピー・文字位置はNOT RUN。**
- `PdfExporterInstrumentedTest`: 2ページ順序、A4寸法、JPEG DCT、元画像不変、全処理fixtureの表示、Letter横向き、グレースケール、白黒しきい値出力を確認。
- `PdfExporterStressInstrumentedTest`: 30ページ出力ケースPASS。実機のピークメモリ・時間測定ではない。
- 合成A4相当3000×4242 pxでの1ページPDFサイズ測定（エミュレータ）:

| 設定 | 出力ラスター | JPEG品質 | PDF容量 |
|---|---:|---:|---:|
| 高画質 | 2479×3505 px（300 DPI上限） | 92 | 330,602 bytes |
| 標準 | 1653×2337 px（200 DPI上限） | 85 | 178,679 bytes |
| 軽量 | 992×1403 px（120 DPI上限） | 70 | 81,964 bytes |

容量順はこの合成fixtureで確認した。実スキャン品質・細線可読性・色差・写真内容への最適設定は未確定。

## 書類検出・補正

- OpenCV Instrumented Testで11 fixtureの処理表示、代表紙面候補、白紙fixture、回転座標、手動四隅の射影出力を確認。
- fixtureの紙面四隅にground truthを作っていないため、検出平均角誤差、検出成功率・誤検知率の旧版対新版比較、アルゴリズム処理時間はNOT RUN。改善幅の数値は出せない。
- 影・照明ムラ・反射の専用補正品質はNOT RUN。TEXT/AUTO/COLORの処理がエラーなくfixtureを出力することと、品質改善の測定は別扱い。

## 未実施・Release前の必須確認

- [ ] CameraX `ImageAnalysis`、ライブ検出枠、複数フレーム安定化、プレビュー／保存画像の座標整合。
- [ ] 実機の手動連続撮影、撮影失敗再試行、権限・バックグラウンド・画面回転。
- [ ] Ground-truth付きOpenCV精度・時間比較、影・反射・細線・数式・色画質比較。
- [ ] 実文書での4言語／混在OCR、簡体字の精度、手書き・数式・表の限界評価。
- [ ] 代表Android/desktop PDF viewerで日本語・英語・中国語・韓国語の検索・選択・コピー・位置・ページ順を確認。
- [ ] CJKフォント埋め込みとviewer互換性の決着。現在のwriterはフォントファイルを埋め込んでいない。
- [ ] 1/10/20/30ページの実機メモリ、処理時間、PDF容量。AAB/ABI別インストールサイズ。
- [ ] ライト／ダーク各画面、画面サイズ・文字拡大・コントラスト・TalkBackを実機確認。
- [ ] Release署名・minify・配布パッケージのビルドと受入。
- [ ] OpenCVのGround truth精度が確立してから、自動検知が安全に候補確定できるしきい値を決める。

したがって、このブランチのDebugビルドと自動テストはPASSだが、vNextの全機能受入およびRelease判定は未完了。
