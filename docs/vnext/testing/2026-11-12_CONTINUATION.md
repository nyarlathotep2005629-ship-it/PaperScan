# PaperScan vNext 継続開発・検証記録

実施日: 2026-11-12 / 対象ブランチ: `codex/paperscan-vnext-foundation`

## 判定

**Release受入: 保留。** 自動テスト、AVD、独立PDF解析の確認範囲は前進したが、物理端末以外にも実画像での検知・補正・OCR品質、GUIビューア操作、障害注入、アクセシビリティ等が残る。「実機確認だけが残る状態」には達していない。

## 変更と根拠

| 要件 | 今回の変更 | 確認済みの範囲 |
|---|---|---|
| REQ-PDF-01 | フォントファイル未埋め込みのCIDFontType2文字層を、不可視Type 3フォントとToUnicode CMapへ変更。異なる文字が255個を超えるページは複数フォントに分割。画像のみPDFには不要なフォントオブジェクトを作らない | PDFBox-Android抽出、Android PdfRendererの不可視表示、独立したpdfplumber抽出とPopplerレンダリングを確認 |
| REQ-SESSION-01 | 主セッションが欠損・破損し、有効な`.bak`が残った場合に復旧。復旧コピーに失敗してもバックアップは残す。同一`SessionStore`内のloadとsaveをMutexで直列化 | 隔離されたテスト領域で3つの障害状態を再現 |
| REQ-PDF-01 | PDF作成中のCoroutineキャンセルを通常の失敗値に変換せず伝搬し、部分PDFを削除 | 制御Fakeでキャンセルを注入 |
| REQ-CAM-02 | 非同期CameraProvider取得後、画面破棄済みならbindしない | コード確認とAVDの既存UIテスト。遅延Future競合そのものの決定的テストは未実施 |
| REQ-DETECT-01 | 合成正解データを陽性10・陰性1へ拡張し、検出率・誤検知・幾何誤差・時間を計測 | 同じ紙多角形を照明・背景・内容だけ変えた小規模データ。実画像ではない |

PDFのType 3採用は、不可視文字層に表示用フォント形状が不要で、フォント同梱・ライセンス依存を避けられるため。PDF 1.4のType 3、ToUnicode、text rendering mode 3を使用する。仕様根拠: [Adobe PDF Reference](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/pdfreference1.4.pdf)。一部のGUIビューアによる選択・検索・コピーの互換性は、この構造とパーサーのPASSだけでは保証しない。

## 自動テストとビルド

基準の2026-11-05の152件から、意味の異なる6件を追加。最終コードで以下を実行した。

| 確認 | 結果 | 数・範囲 |
|---|---|---|
| `testDebugUnitTest` | PASS | Unit 118、失敗0 |
| `connectedDebugAndroidTest` | PASS | API 36 x86_64 AVDで40、失敗0・skip 0。内訳はCompose UI 6、Instrumented integration 34 |
| 合計 | PASS | **158件**、152件比 +6 |
| `lintDebug` | PASS | error 0、warning 20。警告は解消済みとみなさない |
| `assembleDebug` | PASS | Universal Debug APK 235,552,691 bytes |
| `assembleRelease` | PASS | unsigned Release APK 225,756,915 bytes |
| `bundleRelease` | PASS | AAB 92,842,711 bytes。署名ファイルなし。配布可能な署名済みReleaseではない |
| `git diff --check` | PASS | whitespace errorなし |

AVD `emulator-5554`がオンライン、`emulator-5562`はofflineとして除外された。実カメラ・物理端末は使用していない。JUnit XMLは`app/build/test-results/testDebugUnitTest/`と`app/build/outputs/androidTest-results/connected/debug/`、Lint結果は`app/build/reports/lint-results-debug.xml`にある。

## 書類検知・OCR・PDFの計測

| 指標 | 今回値 | 境界 |
|---|---:|---|
| 合成陽性の検出 | 10/10 | すべて同じ四隅の紙を描画した画像。独立形状10件ではない |
| 合成陰性の誤検知 | 0/1 | 無地背景1件のみ。木目のみの陰性画像はない |
| 陽性の平均四隅誤差 | 0.7264 px | 検出成功画像だけの平均 |
| 陽性の平均IoU | 0.9958 | 検出成功画像だけの平均 |
| 検知平均時間 | 42.8 ms | AVDの単発11画像。実機フレーム時間ではない |
| 簡体字合成OCR CER | 0.50 | `简体中文测试` → `筒休中文測试`。前回から改善なし |
| 英語・繁体字・日本語・韓国語の合成短文 | 各全文一致 | 実文書のCERを代表しない |

書類検出アルゴリズム本体は変更していない。旧版・新版の精度差は**該当なし**。小規模合成データだけで精度向上を主張できないため、しきい値や候補スコアを変更しなかった。正解座標はfixture描画スクリプトの紙多角形から定め、検出器の出力からは作っていない。ライセンスと正解四隅を確認できる実写真は今回確保できず、実画像評価はNOT RUN。

検索可能PDFの合成1ページは、PDFBox-Androidで日本語・簡体字・繁体字・韓国語・補助平面文字と複数ページの抽出を確認した。300種類のCJK文字を1ページに置くケースでは複数Type 3フォントへ分割して抽出できた。別系統の`pdfplumber`は合成1ページの27文字を抽出し、領域は`x0=29.75, x1=535.50, top=67.36, bottom=109.46 pt`。元OCR矩形はA4 595×842 ptの正規化値`(0.05, 0.08, 0.90, 0.13)`で、座標が一致した。Poppler `pdfinfo`は1ページ/A4/PDF 1.4を認識し、`pdftoppm`で画像は白紙のまま描画された。これはテスト画像が白紙で、OCR層が不可視である期待結果。合成PDFは無視対象の`work/vnext-evidence/`に置いた。GUIビューアでの検索・選択・コピーはNOT RUN。

同じAVDの合成PDF負荷テストの単発測定。文字層なしページではType 3化に伴い空フォントを省けるが、時間差にはキャッシュ等も含まれるため速度改善とは断定しない。

| ページ | 前回writer（今回の変更前、ms / bytes） | 今回writer（ms / bytes） |
|---:|---:|---:|
| 1 | 105 / 35,787 | 105 / 34,825 |
| 10 | 1,065 / 355,820 | 855 / 346,140 |
| 20 | 2,305 / 711,518 | 1,988 / 692,070 |
| 30 | 3,669 / 1,067,269 | 2,740 / 1,038,001 |

今回writerの合成A4画質別PDF容量は高画質329,640、標準177,717、軽量81,002 bytes。ピークメモリ、実資料画質、OCR・補正の複数回性能はNOT RUN。

## 依存・容量・セキュリティ境界

新しい依存・フォント・権限・外部通信・鍵は追加していない。PDFBox-Androidはテスト専用のまま。Release APKには4 ABI（arm64-v8a、armeabi-v7a、x86、x86_64）のJNIが24ファイル入り、非圧縮JNI合計は各ABIで約36.4/23.5/56.4/72.2 MB。最大ファイルはx86_64のOpenCV `.so` 59.3 MB。ABI別配布は容量削減候補だが、実際の配布形式・端末互換を未確認のまま設定変更しない。R8/minifyは`false`で、難読化・縮小は未実施。

OpenCV 4.13.0の[公式ライセンス](https://opencv.org/license/)はApache 2.0。テスト専用[PdfBox-Android](https://github.com/TomRoush/PdfBox-Android/blob/master/README.md)も主コードApache 2.0。ML Kitは[公式利用条件](https://developers.google.com/ml-kit/terms)の適用対象で、今回の変更による新規同梱はない。解決済み全推移依存のライセンス一覧・NOTICEの網羅検査はNOT RUN。

## 非実機の残作業と再開手順

1. **実画像による検知評価:** 再配布可能な写真と独立四隅アノテーションを確保し、紙の形状・白地・木目陰性・影・端接触・低照度を分けて、旧版との成功/誤検知/未検出/IoU/時間を比較する。改善が確認できた場合だけアルゴリズムを変更する。
2. **補正品質:** 影・照明むら・反射に対する処理は未実装。元画像・正解条件を持つ数式、回路、グラフ、表、手書き、色付き資料でCER/SSIM/細線保持/色差を測ってから追加する。白飛びの復元は受入対象にしない。
3. **OCR品質:** 簡体字CER 0.50が残る。実文書と低解像度・混在・縦書き・表の独立fixtureを作り、前処理/言語選択の比較を行う。エンジン置換は容量、時間、ライセンス、オフライン性の比較後に判断する。
4. **PDF GUI互換:** デスクトップとAndroidの代表ビューアで検索、範囲選択、コピー、複数ページと補助平面文字を確認する。Type 3を受け入れないビューアがあればfont方式を再検討する。
5. **競合・障害:** OCR中の編集/削除/並替え/出力、保存失敗、ディスク満杯、プロセス終了を制御Fakeと隔離データで注入する。特に復旧コピー失敗時に次回保存でバックアップが失われないか確認する。
6. **UI:** 小画面、文字拡大、TalkBack、テーマ切替、画面回転、長文、エラー復旧をAVDとCompose UI Testで確認し、問題が出た箇所を修正する。
7. **配布:** 全依存のライセンス/NOTICEを確認し、ABI別配布サイズ、R8有効時のOCR/JNI互換を別設定で検証する。署名鍵はユーザー指定まで作成・変更しない。

物理端末固有の操作と合格条件は[実機チェックリスト](../DEVICE_ACCEPTANCE_CHECKLIST.md)を参照する。

## Git反映と再開

- コード・テスト: `d01ae28`（検索可能PDF、復旧、キャンセル、Camera bind、検知計測）。
- 検証記録: `1bc7612`。この節の追記は別コミットへ保存する。
- 既存ブランチ`codex/paperscan-vnext-foundation`を維持し、`main`は変更していない。未追跡`APK/`は一切ステージ・変更していない。
- `git push origin codex/paperscan-vnext-foundation`は自動承認審査が2回拒否したためBLOCKED。審査理由は、公開GitHubへの今回の2コミット分の送信について具体的な承認を確認できないというもの。GitHub本人所有とpush権限、既存のリモートブランチは読み取り確認したが、別経路での送信は行わない。
- ユーザーの明示的な再承認と実行環境の許可が得られたら、`git status --short --branch`で`APK/`以外を確認し、`git push origin codex/paperscan-vnext-foundation`を通常実行する。その後`main`との差分を確認してPRを作成する。force pushと署名鍵作成は不要。
