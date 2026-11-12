# PaperScan vNext 第2次開発 テスト実行結果・品質評価

このページは2026-11-05時点の履歴記録。最新のコード・テスト・PDF方式は[2026-11-12 継続開発・検証記録](2026-11-12_CONTINUATION.md)を参照。

実行日: 2026-11-05

実行ブランチ: `codex/paperscan-vnext-foundation`

コード変更前のHEAD: `90ae285363600c178f79a6577b8c0dbeb9c599cc`

検証対象コードコミット: `a483ca4735def45310f5ccc4f591e3bffdad2c49`

評価対象: 上記コミットの第2次開発コード・テスト

## 実行環境

- Windows、Gradle Wrapper 9.4.1、AGP 9.2.0、Kotlin 2.2.10
- Android API 36 x86_64 AVD: `PaperScan_API36(AVD) - 16`
- ADB上の`emulator-5562`はofflineのためGradleがskip。オンラインAVDではInstrumented Test 34件が全件完了。
- 物理端末と実機カメラは使用していない。

## コマンド結果

| コマンド / Check | 結果 | 件数・成果物・Evidence |
|---|---|---|
| `testDebugUnitTest` | PASS | 118 tests、失敗0、エラー0、skip 0。JUnit XML: `app/build/test-results/testDebugUnitTest/`。 |
| `connectedDebugAndroidTest` | PASS | 34 tests、失敗0、skip 0。API 36 AVDで実行。XML: `app/build/outputs/androidTest-results/connected/debug/TEST-PaperScan_API36(AVD) - 16-_app-.xml`。 |
| Compose UI | PASS | 上記34件のうち6件。UIの主要操作の限定的なCompose確認。 |
| Instrumented integration | PASS | 上記34件のうち28件。OpenCV、ML Kit、PDFBox、保存処理等。 |
| `lintDebug` | PASS | エラー0、警告20。依存・SDK更新提案、アイコン/KTX等を含む。Lifecycle Compose 2.9.4にもLintから2.11.0への更新提案が1件ある。 |
| `assembleDebug` | PASS | `app/build/outputs/apk/debug/app-debug.apk`、235,148,895 bytes。Universal APKのファイルサイズ。 |
| `assembleRelease` | PASS | `app/build/outputs/apk/release/app-release-unsigned.apk`、225,756,915 bytes。unsigned。署名済み配布物ではない。複数のJNIライブラリはstripできず、そのまま梱包された。 |
| `git diff --check` | PASS | 最終差分のwhitespace errorなし。 |

`connectedDebugAndroidTest`内のCompose UI Test 5ファイルは旧`createComposeRule()` APIの非推奨コンパイル警告を出した。実行結果には影響せず全6 UIケースはPASS。

実行した主なコマンド:

```powershell
.\gradlew.bat testDebugUnitTest --rerun-tasks
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
.\gradlew.bat connectedDebugAndroidTest
git diff --check
```

計測ログの取得には、テスト完了後に`adb -s emulator-5554 logcat -d -s PaperScanPdfQuality:I PaperScanPerf:I PaperScanDetectionMetric:I PaperScanOcrMetric:I PaperScanOcrPoc:I`を使用した。

テスト内訳はUnit 118 + Instrumented 34 = 152件（PASS 152 / FAIL 0 / 自動テスト未実行 0）。Instrumentedの34件にはCompose UI 6件を含む。前回61件（Unit 36 + Instrumented 25）から91件増えた（Unit +82、Instrumented +9）。前回と今回の分類定義は同じ。

## OpenCV検知の定量確認

API 36 AVD、合成`perspective_a4.png` 1000×800 px、正解四隅を独立JSONに保存した1 fixtureの測定結果:

| 指標 | 今回値 | 試験しきい値 | 判定 |
|---|---:|---:|---|
| 平均四隅誤差 | 0.6107 px | 画像長辺の5%未満（50 px） | PASS |
| 紙領域IoU | 0.9961 | 0.80以上 | PASS |

アノテーションはコードが出した予測座標から作っていない。今回、検出アルゴリズム自体は変更していないため旧版／新版比較は該当なし。ground truthは陽性合成画像1枚だけであり、検出成功率、誤検知率、未検出率、信頼度別正解率、検出時間、実画像性能はNOT RUN。上記の数値を一般的な検知精度として扱わない。

## OCR結果

合成画像をAPI 36 AVD上のbundled ML Kit recognizerで認識した。下記時間は各ケース1回の計測で、実文書や実機速度の保証ではない。

| ケース | 期待 | 実測 | 時間 | 判定・制約 |
|---|---|---|---:|---|
| English | `PaperScan` | `PaperScan` | 110 ms | 合成短文一致 |
| Chinese Simplified | `简体中文测试` | `筒休中文測试` | 103 ms | CER 0.50。既知誤認識を全文fixtureのまま回帰試験化 |
| Chinese Traditional | `繁體中文測試` | `繁體中文測試` | 62 ms | 合成短文一致 |
| Japanese | `日本語テスト` | `日本語テスト` | 98 ms | 合成短文一致 |
| Korean | `한국어` | `한국어` | 109 ms | 合成短文一致 |
| 多言語混在 | 4言語の4行 | 4行を保持。中国語行に誤認識あり | 1,124 ms | 重複抑制とbounds検証はPASS。全文一致ではない |

手動言語指定はLatin、Chinese、Japanese、Koreanの選択ケースとCompose UIで確認した。中国語recognizerは簡体字・繁体字を共通モデルで扱う。WER、実資料の言語別精度、縦書き・手書き・数式・表、文字領域位置誤差はNOT RUN。

## PDF出力・性能測定

PDFBox-Androidで複数ページのCJK・補助平面Unicode抽出とToUnicodeを確認し、Android `PdfRenderer`で不可視文字層が紙面に見えないことを確認した。現行writerはCJKフォントファイルを埋め込んでいない。PDFBox抽出は、一般viewerにおける検索、選択、コピー、表示、位置の保証ではない。外部viewer確認はNOT RUN。

品質設定値は合成A4相当3000×4242 pxを各1ページ出力した1回測定:

| 設定 | 出力ラスター | PDF容量 |
|---|---:|---:|
| 高画質 | 2479×3505 px | 330,602 bytes |
| 標準 | 1653×2337 px | 178,679 bytes |
| 軽量 | 992×1403 px | 81,964 bytes |

30ページ出力stress testは合成ページでPASS。エミュレータの単発時間・容量は次のとおり。これは性能ベンチマークではなく、メモリピークは計測していない。

| ページ数 | 時間 | PDF容量 |
|---:|---:|---:|
| 1 | 105 ms | 35,787 bytes |
| 10 | 1,052 ms | 355,820 bytes |
| 20 | 2,192 ms | 711,518 bytes |
| 30 | 3,187 ms | 1,067,269 bytes |

## 要件対応と未検証

152件すべてを要件ID付きで[マトリクス](TEST_CASE_MATRIX.csv)に割り当てた。これはケースの追跡性であり、全要件の受入済み・全分岐カバーを意味しない。カバレッジとmutation testingはNOT RUN。

次の項目はNOT RUNまたは未実装で、Release受入の残項目とする。

- CameraXの実カメラ初期化・許可／拒否・bind失敗、Previewと実フレームの座標、表示回転、撮影画像との位置差、実機での連続撮影・ライフサイクル。
- 検出の実画像データセット、旧版比較、成功・誤検知・未検出率、信頼度別評価、実機フレーム時間。
- 独立した影・照明むら・反射の推定補正。細線、数式、色、画質差のPSNR/SSIM/エッジ保持/OCR CER定量評価。
- OCR実文書、低解像度・低コントラスト・混在比率別・縦書き・表・手書き・数式。OCRキャンセル、timeout、ページ削除・編集・並替え・出力との遅延競合を制御Fakeで網羅する試験。
- PDF viewerの検索、文字選択、コピー、CJK表示、ページごとの位置整合。フォント未埋め込みの互換性評価。
- 物理端末での1/10/20/30ページ処理時間・ピークメモリ、ライト／ダーク、文字拡大、小画面、TalkBack、アクセシビリティとタッチターゲット。
- ディスク容量不足、プロセス強制終了、電源断時の保存復旧。既存ユーザーデータに触れずテスト用データで安全性を維持する。
- Release署名、minify設定、配布経路、実インストール確認。現行成果物はunsigned APK。

## 主要な修正と残存リスク

テスト追加により、自動orientationが固定用紙で入力画像の向きを無視すること、逆向き四隅・ゼロ長辺・非有限値が不適切に通ること、ラスター寸法計算が大きな整数入力でoverflowすることを検出し修正した。セッションの同時atomic saveでは一時ファイル名を共有する競合があったため、保存をMutexで直列化し失敗時の一時ファイルを削除する。ground-truthのfixture名をログへ出す試験コードがJSONの親階層を誤って参照していたため、計測値は維持しつつdataset IDとfixture名を正しく記録するよう直した。Camera画面も非推奨のCompose LifecycleOwner importからLifecycle Compose APIへ移行した。

上記は対応するUnit/Instrumented試験PASS。物理カメラ、外部PDF互換性、実データ性能、プロセス強制終了復旧は確認していない。よってDebug/Releaseビルドと自動回帰はPASSだが、vNext全体のRelease適格判定はNOT ACCEPTED。
