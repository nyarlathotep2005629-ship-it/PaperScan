# PaperScan テスト要件・テスト設計書 v0.1

作成日: 2026-10-07  
対象: PaperScan Androidアプリ  
参照正本:
- `PaperScan_Requirements_v0.2.md`
- `PaperScan_Basic_Design_v0.1.md`
- `PaperScan_Detailed_Design_v0.1.1.md`

---

## 0. 文書の位置づけ

本書はPaperScanの実装後にテストコードを生成・実行するための、テスト要件、テスト範囲、テストデータ、合否基準、実機確認項目を定義する。

本書の目的は「テスト数を増やすこと」ではなく、次の原則を検証可能にすることである。

1. 元画像や作業セッションの情報を失わない。
2. 20〜30ページ規模でもメモリ不足やクラッシュを起こしにくい。
3. ページ順、向き、アスペクト比を壊さずPDFを生成する。
4. 保存失敗やプロセス終了が発生しても、可能な範囲で再試行できる。
5. Build、Unit Test、Emulator、実機、実資料の検証レベルを混同しない。
6. 将来のためだけに過剰なテスト基盤、mock、DI、テスト専用抽象化を追加しない。

---

# 1. テスト方針

## 1.1 優先順位

テストの優先順位は次の通りとする。

1. データ消失・元画像破壊の防止
2. PDF内容の正しさ
3. セッション復元・失敗時再試行
4. OOM・クラッシュ防止
5. UI操作の主要経路
6. 画像補正品質
7. 性能最適化

見た目の細かな差より、資料としての情報保持と処理完遂性を優先する。

## 1.2 テスト対象の境界

テストは以下の3層に分ける。

| レベル | 実行場所 | 主対象 | 特徴 |
|---|---|---|---|
| Local/JVM | 開発PC | 純粋Kotlinロジック、状態遷移、計算 | 高速・頻繁に実行 |
| Instrumented | Emulator/Android端末 | Android API、Compose UI、URI、PdfDocument | Android実装に依存 |
| Real Device | 実Android端末 | CameraX、実EXIF、メモリ、性能、SAF/共有 | 最終判定に必須 |

Android公式の区分に従い、Android frameworkが不要なロジックは可能な限りLocal Testへ置き、実frameworkが必要な項目だけInstrumented Testへ置く。

## 1.3 テスト結果表記

全検証結果は以下のいずれかで記録する。

```text
PASS     : 実際に実行し、期待結果を満たした
FAIL     : 実際に実行し、期待結果を満たさなかった
NOT RUN  : 未実行、または環境不足により実行できなかった
```

以下を禁止する。

```text
コードがある → PASS
Build成功 → 実機PASS
Emulator成功 → CameraX実機PASS
テスト未実行 → 問題なし
```

---

# 2. テスト技術方針

## 2.1 Local Test

基本候補:

- JUnit
- Kotlin標準assertまたはJUnit assert
- `kotlinx-coroutines-test`

Coroutineを含むテストでは原則 `runTest` を使用し、必要な場合のみTestDispatcherを使用する。

## 2.2 Instrumented / Compose UI Test

基本候補:

- AndroidX Test
- AndroidJUnitRunner
- Jetpack Compose UI Test (`ui-test-junit4` 等)

画面テストでは表示文字列だけに過度に依存せず、主要操作にSemanticsまたは最小限の`testTag`を付けてもよい。

## 2.3 Mock方針

大規模なmock frameworkを必須としない。

優先順位:

```text
純粋関数
↓
Fake実装
↓
小さなstub
↓
必要な場合のみmock
```

実装詳細をmockして「内部メソッドが何回呼ばれたか」を確認するテストより、公開APIから観測できる結果を優先する。

## 2.4 導入しないもの

明確な必要性がない限り、次をテスト目的だけで導入しない。

- Hilt
- 大規模DI Container
- Robolectric
- Snapshot testing基盤
- Golden image専用サーバ
- 独自test framework
- 過剰なPage Object階層

---

# 3. テストID規約

テスト項目は以下のprefixで管理する。

| Prefix | 種別 |
|---|---|
| UT | Local Unit Test |
| VM | ViewModel / State Test |
| ST | Session / Storage Test |
| UI | Compose UI / Instrumented Test |
| INT | Integration Test |
| PDF | PDF生成・構造検証 |
| IMG | 画像処理テスト |
| REC | Recovery / 異常終了 |
| PERF | 性能計測 |
| MEM | メモリ計測 |
| STR | ストレージ計測 |
| DEV | 実機テスト |
| SEC | 権限・非破壊性・プライバシー |

例: `UT-PDF-001`, `UI-SESSION-003`, `DEV-CAMERA-002`。

---

# 4. テストデータ方針

## 4.1 Git管理可能なfixture

リポジトリには自作または配布可能なfixtureを置く。

最低限以下を作成する。

1. `portrait_a4_text.jpg` - 縦A4比、黒文字中心
2. `landscape_graph.png` - 横長、線・グラフ
3. `portrait_color.png` - 色付き図形
4. `small_text_math.png` - 数式、上下付き文字を模した自作画像
5. `rotated_90.jpg` - EXIFまたはfixture上の90度回転
6. `rotated_180.jpg`
7. `flipped.jpg` - flip検証用
8. `non_a4_ratio.png` - A4以外の縦横比
9. `corrupt_image.bin` - 不正画像
10. `large_test_image.jpg` - 高解像度テスト用（Git肥大化に注意）

## 4.2 実授業資料

実際の授業プリントは実機・画質受け入れ試験に使用してよいが、著作権・氏名・学籍番号等が含まれる場合は公開GitHubへcommitしない。

公開テストfixtureには、自作の数式、回路図、グラフ、色線などを用いる。

## 4.3 20〜30ページ試験

同じfixtureを複製してページ数だけ増やす試験と、複数種類を混在させる試験を分ける。

- 同一画像30ページ: メモリ・処理安定性の再現性確認
- 混在30ページ: 縦横比・回転・ページ順・PDF構成確認

---

# 5. Ver.0.1 自動テスト必須範囲

Ver.0.1では以下の機能を自動テストの主対象とする。

- PageItem / ScanSession
- ページ追加・削除・並び替え
- EXIF変換情報の保持
- SessionStore
- PdfPageSizer
- PdfExporter
- SAF保存前後の状態管理
- ScanSessionViewModel
- Compose Home / Session / Export主要経路

CameraX、OpenCV、紙検出、射影補正、画質モードはVer.0.1テストコードの必須対象外とする。

---

# 6. PageItem / ScanSession 単体テスト

## UT-MODEL-001 PageItem生成

**条件**: 正常なURI/workingPath、order、rotation、EXIF情報を与える。  
**期待結果**: 指定値を保持し、Bitmap本体を保持しない。

## UT-MODEL-002 Page order連番化

**条件**: orderが不連続なページ一覧を並べ直す。  
**期待結果**: 表示・出力用orderが0または1始まりのプロジェクト規約どおり連続になる。

## UT-MODEL-003 空セッション

**条件**: pagesが空。  
**期待結果**: PDF作成可能状態にならない。クラッシュしない。

## UT-MODEL-004 選択ページ削除後のselectedPage

**条件**: 選択中ページを削除。  
**期待結果**: 存在しないpageIdを保持しない。隣接ページまたはnullへ安全に遷移する。

## UT-MODEL-005 EXIF情報保持

**条件**: `sourceExifRotationDegrees=90`, `sourceExifFlipped=true`。  
**期待結果**: Session保存・復元後も値が一致する。

---

# 7. ページ操作テスト

## UT-PAGE-001 前へ移動

`[A, B, C]` のCを1つ前へ移動し `[A, C, B]` になること。

## UT-PAGE-002 後ろへ移動

`[A, B, C]` のAを1つ後ろへ移動し `[B, A, C]` になること。

## UT-PAGE-003 先頭を前へ移動

先頭ページに対するmovePreviousで順序が壊れないこと。

## UT-PAGE-004 末尾を後ろへ移動

末尾ページに対するmoveNextで順序が壊れないこと。

## UT-PAGE-005 ページ削除

3ページから中央を削除し、残り2ページの順序が維持されること。

## UT-PAGE-006 全ページ削除

最後の1ページを削除しても例外にならず、Homeまたは空Sessionとして扱える状態になること。

## UT-PAGE-007 重複追加

同一URIを同一セッションへ再追加した場合の仕様どおりの動作を確認する。重複抑止を採用した場合は1件のみ保持する。

---

# 8. PdfPageSizer テスト

本項目はGeminiレビューで修正したpoint/pixel分離を回帰防止する重要テストとする。

## UT-PDF-001 A4 portrait基準

**入力**: portrait A4比画像。  
**期待**: PDF論理ページ寸法が約 `595 x 842 pt` の基準に一致する。

## UT-PDF-002 pixel数非依存

**入力A**: 595x842 px。  
**入力B**: 2975x4210 px。  
**条件**: 同じアスペクト比。  
**期待**: PDFページのpoint寸法は同じ。pixel数をPageInfo寸法として使用しない。

## UT-PDF-003 横長

横長画像でportrait固定による歪みが発生しないページ寸法が算出されること。

## UT-PDF-004 A4以外

B5相当・正方形・縦長画像について、アスペクト比を保った論理ページ寸法を返すこと。

## UT-PDF-005 不正サイズ

width=0、height=0、負値について、明確な失敗またはvalidation結果になること。

## UT-PDF-006 描画矩形

BitmapをPDFページへfitさせる矩形計算が、縦横比を維持しページ外へはみ出さないこと。

---

# 9. EXIF / Orientation テスト

## UT-EXIF-001 0度

回転なし・flipなしで座標と表示向きが変化しない。

## UT-EXIF-002 90度

元画像を90度として扱った際に出力幅高の関係が正しく入れ替わる。

## UT-EXIF-003 180度

180度回転が二重適用されない。

## UT-EXIF-004 270度

270度を90度と誤認しない。

## UT-EXIF-005 flip

`sourceExifFlipped=true` の情報が失われない。

## INT-EXIF-001 preview/export一致

同一fixtureについて、プレビューで解釈した向きとPDF生成で解釈した向きが一致する。

---

# 10. SessionStore テスト

## ST-001 JSON round-trip

ScanSessionを保存し、読み戻した全必須フィールドが一致する。

## ST-002 schemaVersion正常

対応schemaVersionを読み込める。

## ST-003 schemaVersion未知

未来の未知schemaVersionを破壊的に読み換えず、対応外として扱う。

## ST-004 tmpから正常置換

`session.json.tmp` の書き込み・検証後に正本へ置換し、正本が有効JSONである。

## ST-005 tmp書き込み失敗

一時ファイル書き込み失敗時、既存`session.json`を壊さない。

## ST-006 JSON検証失敗

不正tmpを正本へ昇格させない。

## ST-007 置換失敗

正本置換に失敗しても旧`session.json`が残る。成功と誤報しない。

## ST-008 欠損ファイル

session.jsonが存在しない初回起動で正常に新規セッションへ進める。

## ST-009 破損正本

破損session.jsonを検知し、クラッシュではなく復旧不能状態として扱う。

---

# 11. WorkingFileStore テスト

## ST-FILE-001 セッションディレクトリ作成

指定sessionId配下に必要な作業ディレクトリを生成できる。

## ST-FILE-002 他セッション非干渉

Session A削除時にSession Bのファイルを削除しない。

## ST-FILE-003 cleanup

完了したセッションのapp-private作業画像、session JSON、再生成可能キャッシュを設計どおり削除する。

## ST-FILE-004 imported元画像非破壊

Photo Pickerで指定した外部URIの原本を削除・上書きしない。

## ST-FILE-005 cleanup失敗

一部削除失敗でもPDF保存済み事実を失わず、残存ファイルをログ/再cleanup対象として扱う。

---

# 12. ScanSessionViewModel / Coroutine テスト

Coroutineを含むLocal Testでは`kotlinx-coroutines-test`の`runTest`を基本とする。

## VM-001 初期状態

ページ0件、export idle、エラーなしで開始する。

## VM-002 画像追加

Fake PhotoImportServiceから複数PageItemを返し、StateFlowへ正しい順序で反映する。

## VM-003 削除

削除後のpages、selectedPageId、保存要求が一貫する。

## VM-004 並び替え

並び替え後にUI stateと永続化対象の順序が一致する。

## VM-005 PDF生成開始

開始時に二重exportを許可せず、進捗状態へ遷移する。

## VM-006 PDF生成成功

成功時に一時PDF情報を保持し、SAF保存待ち状態へ遷移する。

## VM-007 PDF生成失敗

エラー状態へ遷移するがpages/sessionは保持する。

## VM-008 保存キャンセル

ユーザーがCreateDocumentをキャンセルしてもセッションと一時PDFを保持し、再保存可能状態になる。

## VM-009 保存失敗

I/O失敗でセッションを削除しない。

## VM-010 保存成功

保存成功後のみ、設計されたcleanup/完了処理へ進む。

---

# 13. Photo Picker / Import テスト

Photo PickerそのもののOS UIをLocal Unit Testで再現しない。アプリ側の契約をテストする。

## INT-IMPORT-001 複数URI取込

複数URIを受け取りPageItemへ変換できる。

## INT-IMPORT-002 キャンセル

空結果でエラー扱いせず、既存セッションを変更しない。

## INT-IMPORT-003 persistable permission成功

永続アクセス可能なURIは無条件コピーせず参照方式を維持する。

## INT-IMPORT-004 persistable permission失敗

権限取得失敗時のみapp-private領域へfallback copyし、PageItemはコピー先を使用する。

## INT-IMPORT-005 fallback copy失敗

コピー失敗ページだけをERRORまたはimport失敗として扱い、既存ページを消さない。

## INT-IMPORT-006 EXIF失敗

EXIF取得不能でも画像自体が読める場合は、既定orientationで追加可能とする。

---

# 14. PdfExporter 自動テスト

## PDF-001 1ページ生成

1画像から1ページPDFを生成できる。

## PDF-002 複数ページ生成

3画像から3ページPDFを生成できる。

## PDF-003 順序

入力PageItem順 `[C, A, B]` に対しPDFも同順となる。

検証はページごとに異なる大きな識別文字/図形を持つfixtureを使用する。

## PDF-004 orientation

90/180/270度fixtureが正しい向きでPDFへ描画される。

## PDF-005 aspect ratio

縦、横、非A4比画像が不自然に引き伸ばされない。

## PDF-006 point単位

PDF page boxが画像pixel数ではなく設計されたpoint寸法である。

## PDF-007 破損画像

途中ページがdecode不能の場合、PDF成功と扱わず、失敗ページ番号を返す。セッションは保持する。

## PDF-008 途中I/O失敗

一時PDF書き込み失敗時、未完成PDFを最終成果として扱わない。

## PDF-009 20ページ

20ページfixtureを逐次処理して生成できる。これは機能テストであり、実メモリ合格判定は実機で行う。

## PDF-010 ページ番号一意

PdfDocumentのpageNumberが不正重複せず生成される。

---

# 15. PDF構造・表示確認

PDFテストは「ファイルが存在する」だけでPASSにしない。

## 15.1 自動確認

可能な範囲で次を検査する。

- PDFを再度openできる
- ページ数
- page box寸法
- 各ページが空白だけではない
- 0 byteではない
- ページ順

Android `PdfRenderer` 等を利用する場合はInstrumented Testとする。

## 15.2 レンダリング確認

代表PDFを画像へレンダリングし、次を目視確認する。

- 縦横比
- orientation
- 文字/図形の切れ
- 真っ白/真っ黒ページ
- ページ外クリップ

Ver.0.1では画像補正品質自体は判定対象外。

---

# 16. Compose UI テスト

## UI-HOME-001 初期表示

HomeにPaperScanタイトル、「撮影する」「画像から追加」が表示される。

## UI-HOME-002 撮影未実装表示

Ver.0.1で撮影ボタンを押した際、未実装であることが明確でクラッシュしない。

## UI-SESSION-001 ページ一覧

3ページのstateを与えると3件が表示される。

## UI-SESSION-002 削除操作

削除操作がViewModelの削除イベントへ接続される。

## UI-SESSION-003 並び替え

前後移動操作で画面表示順が変化する。

## UI-SESSION-004 PDFボタン無効

ページ0件ではPDF作成を開始できない。

## UI-SESSION-005 PDFボタン有効

1件以上で仕様どおりPDF作成を開始できる。

## UI-EXPORT-001 進捗表示

export中に `current/total` 相当の進捗が表示される。

## UI-EXPORT-002 エラー表示

PdfExporter失敗時、ユーザー向けメッセージが表示され、ページ一覧が失われない。

## UI-EXPORT-003 再試行

保存失敗後に再保存操作が可能。

---

# 17. Integration Test

## INT-001 Import → Session

fixture URIを取り込み、Session画面へページが表示される。

## INT-002 Session → PDF

ページ追加、削除、並び替え後、その最終状態がPDFへ反映される。

## INT-003 PDF → SAF保存

一時PDFをCreateDocumentで得たURIへコピーし、読めるPDFが保存される。

## INT-004 保存キャンセル → 再保存

最初の保存UIをキャンセル後、再度保存を開始できる。

## INT-005 再起動復元

セッション保存後にActivity/アプリを再生成し、同ページ構成を復元する。

---

# 18. 異常系・Recoveryテスト

## REC-001 import直後終了

再起動時に保存済みページを復元できる、または保存前であることを一貫して扱う。

## REC-002 reorder直後終了

永続化完了した順序を復元できる。

## REC-003 PDF生成途中終了

元ScanSessionを失わない。未完成PDFを完成品として表示しない。

## REC-004 PDF生成完了・保存前終了

可能な場合は一時PDFを再利用、不能なら元セッションから再生成できる。

## REC-005 SAF保存途中失敗

保存先の不完全ファイルを成功扱いせず、アプリ側セッションを保持する。

## REC-006 tmp残存

クラッシュ後に`session.json.tmp`だけ残っていても、正本を不用意に破壊しない。

---

# 19. ストレージ・非破壊性テスト

## STR-001 元画像hash維持

テスト前後でimport元fixtureのhashが一致する。

## STR-002 完了後作業ファイル

正常完了・cleanup後、セッション専用作業画像とsession.jsonが設計どおり削除される。

## STR-003 保存失敗後作業ファイル

保存失敗後は再試行に必要な作業データが残る。

## STR-004 cache肥大化

サムネイルやexport tempが無制限に蓄積しない。

## STR-005 30ページ使用量

実機で以下を計測する。

```text
import元合計
filesDir最大
cacheDir最大
一時PDF
完成PDF
cleanup後残存
```

---

# 20. メモリテスト

## MEM-001 PageItem Bitmap非保持

PageItem/ScanSessionがフル解像度Bitmapをプロパティとして保持しないことをコードレビューでも確認する。

## MEM-002 PDF逐次処理

20〜30ページ生成中、複数フル解像度Bitmapを同時常駐させない実装であること。

## MEM-003 実機peak memory

1/10/20/30ページでpeak memoryを記録する。

固定上限値はv0.1時点では設定しない。ただしOOM、OS kill、著しい継続増加はFAILとする。

## MEM-004 繰り返し生成

同一セッションでPDF生成を複数回実行し、毎回native/Javaメモリが一方向に増え続けないことを確認する。

---

# 21. 性能テスト

現段階では架空の「1ページ3秒以内」等を設定しない。

## PERF-001〜004

以下を実機で計測する。

| ID | ページ数 | 計測 |
|---|---:|---|
| PERF-001 | 1 | total / page time |
| PERF-002 | 10 | total / page time / peak memory |
| PERF-003 | 20 | 同上 |
| PERF-004 | 30 | 同上 |

計測時は端末モデル、Android版、画像解像度を記録する。

性能改善Issueを作る条件:

- ユーザー本人の実用上明確に待ち時間が長い
- ページ数増加に対して異常な非線形悪化
- profilerで明確なボトルネックを確認

---

# 22. Android実機テスト

Ver.1.0までに以下を実機で確認する。

## DEV-001 Photo Picker

実端末で複数画像を選択し、再起動後のアクセス可否を確認する。

## DEV-002 SAF Downloads保存

端末のローカルDocuments/Downloads相当へ保存できる。

## DEV-003 SAF Google Drive Provider

Google DriveがDocumentsProviderとして利用可能な端末では、Drive保存を確認する。未導入端末ではNOT RUNでよい。

## DEV-004 Android共有

完成PDFをAndroid共有UIへ渡せる。

## DEV-005 background

PDF生成/Session操作中にHomeへ移動し復帰して状態が破綻しない。

## DEV-006 画面OFF/ON

画面OFF/ON後にアプリ状態が不正にならない。

## DEV-007 process recreation

Developer options等を利用可能な範囲でActivity/process recreationを確認する。

## DEV-008 20〜30ページ

実機で20〜30ページを処理し、OOMやクラッシュがない。

---

# 23. Ver.0.3以降 画像処理テスト

OpenCV導入後に有効化する。Ver.0.1ではテストコードを先行実装しない。

## IMG-001 手動四隅射影

既知の4点から期待する長方形へ射影される。

## IMG-002 四隅順序

入力順が異なってもP0=左上、P1=右上、P2=右下、P3=左下へ正規化される。

## IMG-003 self-intersection

交差する四隅をvalidationで拒否する。

## IMG-004 自動検出成功

自作fixtureで紙四隅が許容誤差内に入る。

## IMG-005 自動検出失敗

紙が判定不能な画像を誤ってHIGH confidenceで成功扱いしない。

## IMG-006 rotation + detection

EXIF適用後の座標系で検出結果が一貫する。

---

# 24. Ver.0.5以降 画質テスト

画質はpixel完全一致を基本判定にしない。

固定fixtureと実資料を用いて、以下を比較する。

- 黒文字
- 数式/添字
- 細い回路線
- グラフ罫線
- 赤字
- カラー配線
- 薄い鉛筆/印刷
- 影
- 黄ばみ/色かぶり

## AUTO

一般資料で色を不必要に失わず読みやすくなること。

## TEXT

文字コントラストを高めても細線・添字を消しすぎないこと。

## COLOR

色差が意味を持つ資料の色を保持すること。

目視判定が必要なテストは自動PASSにせず、レビュー結果をEvidenceとして残す。

---

# 25. CameraXテスト（Ver.0.6以降）

CameraXは実機確認を必須とする。

## DEV-CAM-001 撮影

1ページ撮影しapp-private領域へ保存できる。

## DEV-CAM-002 連続撮影

10ページ以上連続撮影し、ページ欠落・重複・クラッシュがない。

## DEV-CAM-003 EXIF

端末カメラが生成した実EXIF orientationを正しく扱う。

## DEV-CAM-004 ギャラリー非登録

仕様どおり、PaperScan内部撮影画像が自動的に一般ギャラリーへ大量登録されない。

## DEV-CAM-005 権限拒否

CAMERA権限拒否時にクラッシュせず説明・再試行経路を提供する。

---

# 26. セキュリティ・権限テスト

## SEC-001 Manifest権限

初期版で不要な以下の権限が存在しないこと。

- `INTERNET`
- `MANAGE_EXTERNAL_STORAGE`
- 不要な広範写真read/write権限

CameraX導入後は`CAMERA`のみ必要性を確認する。

## SEC-002 外部送信なし

初期版が資料画像をネットワーク送信するコード/SDKを含まないこと。

## SEC-003 FileProvider範囲

共有用FileProviderを導入した場合、公開範囲が必要なexport cache等に限定される。

## SEC-004 元ファイル非破壊

import元URIに対するdelete/write操作を行わない。

---

# 27. Ver.0.1 受け入れテストマトリクス

| AC | テスト | 必須 |
|---|---|---|
| 2枚以上選択 | INT-IMPORT-001 / UI | 必須 |
| ページ一覧 | UI-SESSION-001 | 必須 |
| 削除 | UT-PAGE-005 / UI-SESSION-002 | 必須 |
| 並び替え | UT-PAGE-001〜004 / UI-SESSION-003 | 必須 |
| PDF順序 | PDF-003 | 必須 |
| 非引き伸ばし | UT-PDF-003/004/006 + PDF-005 | 必須 |
| SAF保存 | INT-003 | 必須 |
| 保存失敗でsession保持 | VM-009 / REC-005 | 必須 |
| 再起動復元 | ST-001 / INT-005 | 必須 |
| 元画像非破壊 | ST-FILE-004 / STR-001 | 必須 |
| 20ページ安定 | PDF-009 + DEV-008 | 自動+実機 |
| pixel/point分離 | UT-PDF-001/002 + PDF-006 | 必須 |

Ver.0.1のリリース判定では、自動テストPASSだけでなく最低1台のAndroid実機で主要フローを確認する。

---

# 28. Ver.1.0 受け入れ条件との対応

要件定義v0.2のAC-01〜AC-13に対し、以下のEvidenceを揃える。

| 要件AC | 主なEvidence |
|---|---|
| AC-01 撮影 | DEV-CAM系列 |
| AC-02 紙領域検出 | IMG-004 + 実資料 |
| AC-03 手動救済 | IMG-001 + UI PageEdit |
| AC-04 台形補正 | IMG-001 + visual QA |
| AC-05 文字・数式 | 画質fixture + 実資料レビュー |
| AC-06 図面・回路図 | 細線fixture + 実資料レビュー |
| AC-07 カラー | COLOR fixture + 実資料レビュー |
| AC-08 ページ管理 | UT-PAGE + UI-SESSION |
| AC-09 PDF | PDF系列 |
| AC-10 保存・共有 | INT-003 / DEV-002〜004 |
| AC-11 一時データ | STR系列 |
| AC-12 安定性 | MEM / PERF / DEV-008 |
| AC-13 容量 | STR-005 + PDF size記録 |

---

# 29. テストコード配置

初期案:

```text
app/
├─ src/main/java/com/paperscan/...
├─ src/test/java/com/paperscan/
│  ├─ model/
│  │  └─ PageOperationsTest.kt
│  ├─ pdf/
│  │  └─ PdfPageSizerTest.kt
│  ├─ storage/
│  │  └─ SessionSerializationTest.kt
│  └─ viewmodel/
│     └─ ScanSessionViewModelTest.kt
│
└─ src/androidTest/java/com/paperscan/
   ├─ ui/
   │  ├─ HomeScreenTest.kt
   │  ├─ SessionScreenTest.kt
   │  └─ ExportScreenTest.kt
   ├─ pdf/
   │  └─ PdfExporterInstrumentedTest.kt
   └─ integration/
      └─ Ver01FlowTest.kt
```

実装構造に合わせて名称は調整してよい。テストのためだけにproductionパッケージ構造を大きく変更しない。

---

# 30. 推奨テストコード実装順

Codexにテストコード生成を依頼する場合、以下の順で行う。

1. `PdfPageSizerTest`
2. Page delete/reorder純粋ロジック
3. Session JSON round-trip/schema
4. `ScanSessionViewModelTest`
5. `PdfExporter`の小規模fixture test
6. Compose Session UI test
7. Ver.0.1 integration test
8. 実機validation用手順/計測コード

理由は、最も壊れやすく高速に検証できる純粋ロジックから固めるためである。

---

# 31. テストコード生成時の禁止事項

Codexは以下を行わない。

- private実装の呼び出し回数だけを検証する脆いテスト
- 全依存をmock化する
- productionコードをテストだけのために大規模再設計する
- Android framework不要なテストをすべてinstrumentedにする
- 実機未確認なのにPASSを記録する
- 時間依存の`Thread.sleep()`を大量に使用する
- ランダム値依存で再現不能なテストを作る
- 1テストで多数の独立仕様を検証する
- fixtureに実授業資料や個人情報をcommitする
- PNG/PDF等の大容量fixtureを無制限に追加する

---

# 32. テストコード品質要件

各テストは原則として、テスト名から次が分かる形式にする。

```text
対象_条件_期待結果
```

例:

```kotlin
@Test
fun calculatePageSize_sameAspectDifferentPixels_returnsSamePointSize()
```

テスト内は原則Arrange / Act / Assertが判別可能な構造にする。

同一初期化が重複する場合のみfixture/helperへ抽出する。抽象化自体を目的にhelperを増やさない。

---

# 33. Coroutineテスト要件

- suspend関数は`runTest`でテストする。
- 実時間delay待ちを避け、virtual timeを利用する。
- 複数TestDispatcherを使う場合は同一`TestCoroutineScheduler`を共有する。
- Production側でDispatcherを切り替えるコードがあり、テストが不安定になる場合のみDispatcher注入を検討する。
- テスト容易性だけを理由にアプリ全体へDI frameworkを導入しない。

---

# 34. UIテスト安定性

- Compose test APIのidle synchronizationを利用する。
- `Thread.sleep()`でUI待ちを実装しない。
- 文字列変更で全テストが壊れないよう、主要アクションはSemantics role/contentDescription/testTagを適切に利用する。
- testTagはテスト専用IDの乱造を避け、操作上重要な要素だけに付ける。
- OS Photo Pickerそのものの内部UI文言や配置をPaperScan側のテストで固定しない。

---

# 35. PDF目視QA記録

PDFに関する代表テストでは、最低限次を記録する。

```text
端末/Emulator:
Android version:
入力fixture:
ページ数:
生成PDFサイズ:
ページbox:
向き:
アスペクト比:
表示確認アプリ:
結果: PASS / FAIL / NOT RUN
備考:
```

PDF表示アプリ固有の問題とPaperScan生成不良を区別するため、重大な表示問題が出た場合は可能なら2種類のviewerで確認する。

---

# 36. 実機テスト記録

実機検証では以下を記録する。

```text
Device:
Android:
RAM:
Free storage before:
Input page count:
Input total size:
Output PDF size:
Processing time:
Peak memory (取得可能な範囲):
Free storage after cleanup:
Result:
Notes:
```

端末名や個人情報を公開リポジトリへ記録する必要はなく、ローカルEvidenceでもよい。

---

# 37. 回帰テスト

以下の変更を行った場合、関連テストを必ず再実行する。

| 変更 | 最低限再実行 |
|---|---|
| PageItem/Session | UT-MODEL, ST, VM |
| reorder/delete | UT-PAGE, UI-SESSION, PDF-003 |
| PdfPageSizer | UT-PDF全件, PDF-005/006 |
| EXIF処理 | UT-EXIF, INT-EXIF, PDF-004 |
| SessionStore | ST全件, REC |
| PdfExporter | PDF全件, INT-002/003 |
| SAF | INT-003/004, DEV-002/003 |
| OpenCV | IMG + PDF + 実資料 |
| CameraX | DEV-CAM + Session復元 |

---

# 38. テスト失敗時の扱い

テスト失敗時は、最初からproductionコードを書き換えず、以下を区別する。

1. 実装不具合
2. テスト期待値の誤り
3. fixture不備
4. Android/OEM環境差
5. テスト環境・SDK問題
6. flaky test

flaky testを「もう一度通ったからPASS」としない。再現条件を確認し、原因が不明なら未解決として扱う。

---

# 39. CI / 自動実行

GitHub Actions等のCIは必須ではない。

初期個人開発ではローカルで以下が安定実行できればよい。

```text
Debug Build
Local Unit Tests
Android Lint
Instrumented Test（Emulatorが利用可能な場合）
```

リポジトリ運用が安定してから、必要ならCIへ移行する。

CI導入のためだけに開発を止めない。

---

# 40. Ver.0.1 テスト完了条件

Ver.0.1のテスト工程は、最低限以下を満たした時点で完了とする。

1. Local Unit TestsがPASS。
2. `PdfPageSizer`のpixel/point分離テストがPASS。
3. Session save/restoreの主要テストがPASS。
4. ViewModelの成功・失敗・キャンセル経路がPASS。
5. Compose主要画面テストがPASS、または環境不足ならNOT RUNとして理由が明記される。
6. 2〜3ページfixtureで実PDFを生成し、ページ数・順序・向き・縦横比を確認する。
7. SAF保存の正常/キャンセル/失敗経路を確認する。
8. 元画像が変更されていないことを確認する。
9. 20ページ処理を少なくとも1回実行する。
10. Android実機で主要フローを1回以上完遂する。
11. 未実行項目をPASS扱いしていない。

---

# 41. Ver.1.0 テスト完了条件

要件定義v0.2のAC-01〜AC-13を対応Evidenceで満たし、さらに以下を満たす。

- 自動紙検出失敗時も手動補正で完遂できる。
- 実授業資料の文字・数式・回路図・細線が判読できる。
- COLORモードで意味のある色を保持する。
- 30ページ程度を実機で完遂する。
- 保存失敗・生成失敗から再試行できる。
- 正常完了後に不要な作業原稿が残り続けない。
- imported元画像を変更・削除しない。
- 重大なmemory leakが継続再現しない。

---

# 42. Codexへテスト実装を依頼する際の完了報告

Codexはテストコード作成後、最低限以下を報告する。

## Added Tests

- 追加したtest file
- 対応するテストID
- 検証する仕様

## Execution

```text
Debug Build: PASS / FAIL / NOT RUN
Local Unit Test: PASS / FAIL / NOT RUN
Lint: PASS / FAIL / NOT RUN
Instrumented Test: PASS / FAIL / NOT RUN
Emulator: PASS / FAIL / NOT RUN
Android実機: PASS / FAIL / NOT RUN
PDF visual QA: PASS / FAIL / NOT RUN
```

## Failures

- failing test
- 原因
- production不具合かtest不具合か
- 未解決事項

## Coverage Gaps

自動化できていないものを明記する。

例:

- CameraX実機品質
- Google Drive DocumentsProvider
- 30ページpeak memory
- 実授業資料の画質

---

# 43. 実装後にCodexへ渡すテスト生成指示の要約

実装後のCodexには次を守らせる。

```text
1. 最新の要件定義、基本設計、詳細設計、本テスト設計を読む。
2. 実装済みコードを先に確認し、存在しないAPIを想像してテストを書かない。
3. Ver.0.1の必須テストIDを優先する。
4. Local Testで済むものをInstrumented Testにしない。
5. Fakeを優先し、過剰なmock/DIを追加しない。
6. テスト失敗を隠すために期待値を書き換えない。
7. production不具合を発見した場合は、テストを弱めず不具合として報告する。
8. 実行可能なBuild/Testを実際に実行する。
9. 未実行項目はNOT RUNとする。
10. Scope外のCameraX/OpenCV等のテストをVer.0.1で先行実装しない。
```

---

# 44. 参考となるAndroid公式テスト方針

本書はAndroid公式の基本的な区分と整合させる。

- Local tests: 開発PCのJVM上で高速に実行し、Android framework不要なロジックを検証する。
- Instrumented tests: Android device/emulator上でframework APIやUIを検証する。
- Compose UI tests: Compose semanticsと同期機構を利用して主要UI挙動を検証する。
- Coroutine tests: `kotlinx-coroutines-test`の`runTest`等を用いて非同期処理を決定的に検証する。

依存ライブラリの具体的versionは、テストコード実装開始時点の安定版と既存プロジェクトのversion catalog/Gradle設定を優先し、本書では固定しない。

---

# 45. 最終方針

PaperScanのテストは、テストコード数やcoverage率そのものを目的にしない。

最重要なのは、

```text
元画像を壊さない
↓
セッションを失わない
↓
正しい順序・向き・比率でPDFを作る
↓
失敗しても再試行できる
↓
20〜30ページでも実機で完遂できる
↓
最終的に実授業資料が読みやすい
```

というプロダクトの価値を、各検証レベルで確認することである。

自動テストで確認できる項目は自動化し、Android実機・CameraX・画質・性能など実環境でしか判断できない項目は、無理に自動PASSへ変換せず実機Evidenceとして残す。
