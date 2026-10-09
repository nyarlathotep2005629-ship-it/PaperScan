**PaperScan**

**基本設計書 v0.1**

Android向け 紙資料撮影・補正・PDF化アプリ

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p><strong>設計の中心</strong></p>
<p><strong>読みやすいPDF / 情報を失わない補正 /
安定した20〜30ページ処理</strong></p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

作成日: 2026-10-07

技術方針: Kotlin主体。C++ / NDKは実測上の必要性が生じた場合のみ追加。

# 1. 文書の目的

本書は、要件定義書 v0.2
を実装可能な構造へ落とし込むための基本設計を定める。対象アプリは、Androidスマートフォンで撮影した紙資料を、撮影、紙検出、切り抜き、歪み補正、画質補正、複数ページPDF化することに特化する。クラウド管理、OCR、AI、授業管理等は本設計の対象外とする。

# 2. 設計原則

> **・**PDFとしての読みやすさを最優先する。
>
> **・**見栄えよりも、文字・数式・回路図・細線などの原稿情報を失わないことを優先する。
>
> **・**20〜30ページ程度でもメモリ不足やクラッシュを起こしにくい構造とする。
>
> **・**授業中でも迷いにくい単純な操作を維持する。
>
> **・**ストレージを無駄に消費せず、作業完了後の一時データを残さない。
>
> **・**将来必要かもしれないという理由だけで、複雑な抽象化や依存関係を追加しない。

# 3. 対象プラットフォーム

Android専用とする。初期案として minSdk は Android 10 / API 29
を候補とし、targetSdk と compileSdk
は実装開始時点の安定版SDKを使用する。Preview版SDKを必須とはしない。

# 4. 使用技術

| **項目**        | **採用技術**                                         |
|-----------------|------------------------------------------------------|
| 主言語          | Kotlin                                               |
| IDE             | Android Studio                                       |
| UI              | Jetpack Compose                                      |
| UI状態管理      | ViewModel + StateFlow                                |
| 撮影            | CameraX                                              |
| 既存画像選択    | Android Photo Picker                                 |
| 画像処理        | OpenCV                                               |
| PDF生成         | Android PdfDocumentを第一候補                        |
| PDF保存         | Storage Access Framework                             |
| 非同期処理      | Kotlin Coroutines                                    |
| 作業ファイル    | Android App-specific Storage                         |
| C++ / NDK / JNI | 初期版では使用しない。性能上必要になった場合のみ追加 |

# 5. 初期版で採用しない技術

> **・**Room / SQLite
>
> **・**Hilt等のDIフレームワーク
>
> **・**Repositoryパターン
>
> **・**Clean Architectureの多層構造
>
> **・**サーバー / API / アカウント / クラウドDB
>
> **・**C++ / NDK
>
> **・**Pythonランタイム

必要性が発生してから追加する。

# 6. システム全体構成

Jetpack Compose UI  
Home / Camera / Session / Page Edit / Export  
\|  
v  
ScanSessionViewModel  
- ページ一覧  
- 選択ページ  
- 処理状態  
- 画質モード  
\|  
+-----+-----------+  
v v  
CameraService DocumentProcessor ----\> OpenCV  
\| \|  
+--------+--------+  
v  
WorkingFileStore  
\|  
v  
App-specific Storage  
\|  
v  
PdfExporter

アプリ規模が小さいため大規模なレイヤー分割は行わない。ただしUIと画像処理コードは直接結合せず、将来C++へ置き換えられる境界として
DocumentProcessor を持つ。

# 7. 画面構成

## 7.1 Home画面

\[ 撮影 \]  
\[ 画像から追加 \]  
  
前回終了していない作業がある場合のみ:  
\[ 続きから \] \[ 破棄 \]

## 7.2 Camera画面

> **・**カメラプレビュー
>
> **・**撮影ボタン
>
> **・**フラッシュ設定
>
> **・**戻る
>
> **・**現在ページ数

初期版ではAIシャッター、自動撮影、リアルタイム高精度紙検出は必須としない。撮影後に紙検出を行う方式から開始する。

## 7.3 Session画面

ページサムネイル: 1 2 3 4 5  
  
\[ 現在ページのプレビュー \]  
  
\[ 編集 \] \[ 削除 \]  
\[ + ページ追加 \]  
\[ PDFを作成 \]

## 7.4 Page Edit画面

> **・**自動検出された紙領域表示
>
> **・**四隅の手動移動
>
> **・**回転
>
> **・**画質モード選択
>
> **・**補正前後プレビュー
>
> **・**再検出

# 8. 紙領域と座標設計

紙の四隅は P0=左上、P1=右上、P2=右下、P3=左下
の順で固定する。座標は実ピクセルだけでなく、0〜1の正規化座標として保持する。これによりプレビュー用縮小画像とフル解像度画像で同じ紙位置情報を使用できる。

P0 ---------------- P1  
\| \|  
\| PAPER \|  
\| \|  
P3 ---------------- P2

# 9. データモデル

## 9.1 ScanSession

ScanSession  
id  
createdAt  
pages  
selectedPageId  
state

## 9.2 PageItem

PageItem  
id  
sourceType // CAMERA / IMPORT  
sourceUri / workingPath  
thumbnailPath  
corners  
rotation  
scanMode // AUTO / TEXT / COLOR  
order  
status // READY / NEEDS_REVIEW / PROCESSING / ERROR

# 10. メモリ管理方針

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p><strong>フル解像度BitmapをPageItem内に保持しない</strong></p>
<p><strong>必要になったページだけ読み込み、処理後に解放する</strong></p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

20〜30枚の高解像度画像を同時展開するとRAMを大量に消費するため、PageItemではURIまたはファイルパスを保持し、画像は必要時だけデコードする。

# 11. 作業ファイル設計

## 11.1 進行中セッション

filesDir/  
scan_sessions/  
\<session-id\>/  
session.json  
captures/  
page001.jpg  
page002.jpg  
...

進行中の原稿は cacheDir
だけに依存させない。Androidは空き容量が不足するとキャッシュを削除できるため、作業中原稿はアプリ専用の永続領域に置き、完了または明示的破棄時に削除する。

## 11.2 cacheDir

cacheDir/  
thumbnails/  
export/  
result_temp.pdf

サムネイル、一時プレビュー、PDF生成途中ファイルなど、再生成可能なデータだけを置く。

# 12. カメラ撮影画像と既存画像

## 12.1 CameraX撮影

撮影画像は filesDir/scan_sessions/\<id\>/captures/
へ直接保存し、Androidの写真アプリへ自動登録しない。PDF完成後に作業画像を削除する。

## 12.2 Photo Pickerからの読み込み

Photo
Pickerで選択された元画像は移動、書き換え、削除をしない。アプリ側ではURIを保持し、元画像に対して破壊的操作を行わない。

# 13. セッション保存と復元

RoomやSQLiteは使用せず、小規模な session.json
を保存する。OSによってアプリが終了しても、再起動後に未完了セッションを検出して復元できる構造とする。

{  
"sessionId": "abc123",  
"pages": \[  
{"id":"page1", "rotation":0, "mode":"AUTO"}  
\]  
}

# 14. 画像処理パイプライン

## 14.1 Stage A: プレビューと紙検出

原画像  
-\> EXIF回転補正  
-\> 縮小  
-\> グレースケール化  
-\> ノイズ軽減  
-\> エッジ検出  
-\> 輪郭抽出  
-\> 四角形候補抽出  
-\> 候補評価  
-\> 紙の四隅

紙検出は軽量化した画像で行い、フル解像度画像での常時検出は避ける。

## 14.2 四角形候補評価

> **・**面積
>
> **・**四辺形であるか
>
> **・**画像内で占める割合
>
> **・**辺の直線性
>
> **・**極端な形状ではないか

単純に最大輪郭だけを採用せず、候補を評価して最も妥当なものを紙として扱う。

## 14.3 検出信頼度

HIGH / MEDIUM / LOW

LOWの場合は「紙の範囲を確認してください」と表示し、四隅の手動修正を促す。誤検出状態をそのまま確定しない。

# 15. 射影補正と画質補正

## 15.1 射影補正

確定した四隅からOpenCVのPerspective
Transformを用いて、斜め撮影画像を矩形原稿へ変換する。

## 15.2 基本処理順

Perspective correction  
-\> White balance  
-\> Brightness correction  
-\> Contrast correction  
-\> Noise reduction  
-\> Optional sharpening  
-\> Resize

処理順や補正強度は実際の授業資料で比較し、机上で固定値を決めない。

# 16. 画質モード

| **モード** | **目的**                                     | **補正方針**                                                               |
|------------|----------------------------------------------|----------------------------------------------------------------------------|
| AUTO       | 一般的な授業プリント                         | 色を維持しつつ紙背景、明るさ、コントラスト、軽度の色かぶりを補正           |
| TEXT       | 白黒文章中心                                 | 背景補正とコントラストを強める。ただし細線、薄い文字、数式、添字を消さない |
| COLOR      | カラー回路図、グラフ、写真、赤字、マーカー等 | 色保持を優先し、AUTOより補正を弱くする                                     |

# 17. PDF出力設計

## 17.1 ページ単位処理

page 1: decode -\> perspective -\> quality -\> resize -\> PDFへ追加 -\>
Bitmap解放  
page 2: decode -\> perspective -\> quality -\> resize -\> PDFへ追加 -\>
Bitmap解放  
...

| **Full-resolution processing = 1 page at a time** |
|---------------------------------------------------|

初期版では高速化のための並列処理を行わず、メモリ安定性を優先する。

## 17.2 PDF解像度

スマートフォンの原画像をそのまま埋め込まず、資料として十分な解像度へ縮小する。初期検証値は長辺2400〜3000px程度から開始し、小さい文字、数式、回路図を用いて評価する。

## 17.3 PDF生成方式

Android標準の PdfDocument
を第一候補とする。ファイルサイズ、JPEGの扱い、出力品質、20〜30ページ時の性能を実測し、不十分な場合のみ別ライブラリを検討する。

# 18. PDF生成と保存フロー

\[ PDFを作成 \]  
-\> 出力パラメータ確定  
-\> cacheDirへ一時PDF生成  
-\> 生成成功確認  
-\> Android保存画面  
-\> ユーザーが保存先選択  
-\> 選択URIへコピー  
-\> 保存成功  
-\> 作業セッション削除

途中で失敗した場合は作業画像を削除せず、再試行できる状態を維持する。保存にはStorage
Access Frameworkを用い、Google Drive
APIは実装しない。保存後の共有はAndroid標準共有UIを利用する。

# 19. 削除ポリシー

| **状況**                   | **扱い**                                                 |
|----------------------------|----------------------------------------------------------|
| PDF生成・保存成功          | CameraX作業画像、session.json、サムネイル、一時PDFを削除 |
| PDF生成失敗                | 作業データを保持し、再試行可能にする                     |
| Photo Pickerで選んだ元画像 | 絶対に削除しない                                         |

# 20. エラー設計

| **エラー**     | **ユーザーへの対応**                                 |
|----------------|------------------------------------------------------|
| 紙検出失敗     | 自動検出できなかったことを表示し、手動四隅指定へ誘導 |
| 画像読込失敗   | 該当ページをERROR状態にし、他ページと混同しない      |
| PDF生成失敗    | 失敗したページ番号を示し、作業データは保持           |
| ストレージ不足 | 処理を中止し、作業データを保持                       |

# 21. 非同期処理と進捗表示

画像処理、ファイルI/O、PDF生成はUIスレッド上で実行しない。Kotlin
Coroutinesを用い、処理中は進捗を表示する。

PDFを作成しています  
12 / 28 ページ  
\[########------\]

# 22. C++追加境界

初期:  
DocumentProcessor -\> OpenCvDocumentProcessor -\> Kotlin / OpenCV API  
  
将来必要時:  
DocumentProcessor -\> NativeDocumentProcessor -\> JNI -\> C++ -\> OpenCV
C++

## 22.1 C++導入条件

> **・**処理速度が実用上遅いことが実測された
>
> **・**メモリ使用量が問題になる
>
> **・**Kotlin/Java OpenCV APIでは実装困難
>
> **・**C++資産再利用の方が明確に簡単
>
> **・**プロファイル結果から画像処理がボトルネックと判明

「C++の方が速そう」という理由だけでは追加しない。

# 23. 初期パッケージ構成

com.paperscan  
MainActivity.kt  
ui/  
HomeScreen.kt  
CameraScreen.kt  
SessionScreen.kt  
PageEditScreen.kt  
ExportScreen.kt  
model/  
ScanSession.kt  
PageItem.kt  
CornerPoints.kt  
ScanMode.kt  
scan/  
DocumentDetector.kt  
DocumentProcessor.kt  
camera/  
CameraService.kt  
pdf/  
PdfExporter.kt  
storage/  
WorkingFileStore.kt  
SessionStore.kt  
viewmodel/  
ScanSessionViewModel.kt

Domain / Application / Infrastructure / Repository / UseCase / Factory
等の多層構造は導入せず、必要になった責務だけを分離する。

# 24. 権限設計

初期版で基本的に必要な権限はCAMERAのみとする。端末内画像はPhoto
Picker、PDF保存はStorage Access
Frameworkを利用し、広範なストレージアクセス権限を要求しない。

# 25. テスト設計

| **レベル**           | **主な確認内容**                                                           |
|----------------------|----------------------------------------------------------------------------|
| Level 1: Kotlin単体  | ページ並び替え、CornerPoints、座標変換、ファイル名、session.json、状態遷移 |
| Level 2: 画像処理    | 正面/斜め/暗い/影/白机/木目/カラー/回路図/数式/薄い文字で比較              |
| Level 3: Emulator    | 画面遷移、ページ管理、保存、エラー処理                                     |
| Level 4: Android実機 | CameraX、高解像度画像、メモリ、処理速度、Photo Picker、PDF保存、共有       |

Build/Test
PASSと実機での正常動作は別の検証レベルとして扱う。特にCameraXと高解像度処理は実機確認を必須とする。

# 26. 画質・性能・ストレージ評価

## 26.1 画質評価資料

> **・**小さい文字
>
> **・**数式・添字
>
> **・**グラフ
>
> **・**表
>
> **・**回路図・細い配線
>
> **・**赤字
>
> **・**カラー図

## 26.2 性能評価

1ページ、10ページ、20ページ、30ページでPDF生成を試験し、クラッシュ、OutOfMemory、UIフリーズ、ページ順、画質維持、作業データ削除を確認する。

## 26.3 ストレージ評価

元写真合計サイズ、PDFサイズ、作業中最大使用量、終了後残存容量を測定する。「PDFにしたから小さくなったはず」と推測で判断しない。

# 27. Ver.1.0の基本処理

起動  
-\> 撮影  
-\> 自動紙検出  
-\> 必要なら四隅修正  
-\> ページ追加  
-\> ページ一覧確認  
-\> PDF作成  
-\> 1ページずつ高解像度処理  
-\> PDF完成  
-\> 保存  
-\> 共有  
-\> 作業データ削除

# 28. Ver.0.1の実装範囲

Photo Pickerで画像を複数選択  
-\> PageItemとして表示  
-\> ページ並び替え  
-\> PdfDocumentでPDF作成  
-\> ACTION_CREATE_DOCUMENTで保存

Ver.0.1ではCameraX、OpenCV、紙検出、射影補正はまだ実装しない。目的は、Android上で複数画像を安全に扱い、ページ順を維持して1つのPDFへ保存できることの確認である。

# 29. 段階的な追加順

| **バージョン** | **内容**               |
|----------------|------------------------|
| Ver.0.1        | 画像 -\> PDF           |
| Ver.0.2        | Session / ページ管理   |
| Ver.0.3        | 手動Crop + Perspective |
| Ver.0.4        | OpenCV自動紙検出       |
| Ver.0.5        | 画質補正               |
| Ver.0.6        | CameraX                |
| Ver.0.7        | AUTO / TEXT / COLOR    |
| Ver.1.0        | 実用版                 |

# 30. 基本設計上の最重要事項

<table>
<colgroup>
<col style="width: 100%" />
</colgroup>
<thead>
<tr class="header">
<th><p><strong>フル解像度画像を大量にRAMへ保持しない</strong></p>
<p><strong>作業中原稿をcacheだけに依存しない</strong></p>
<p><strong>ユーザーの元画像を削除しない</strong></p>
<p><strong>補正失敗時に原稿情報を失わせない</strong></p>
<p><strong>C++は必要性を測定してから追加する</strong></p></th>
</tr>
</thead>
<tbody>
</tbody>
</table>

# 31. 設計ステータス

## 31.1 現時点で確定

> **・**Android専用
>
> **・**Kotlin主体
>
> **・**Jetpack Compose
>
> **・**CameraX
>
> **・**OpenCV
>
> **・**PDF化特化
>
> **・**ローカル処理
>
> **・**Google Drive APIは実装しない
>
> **・**C++は必要時のみ追加
>
> **・**ページ単位処理
>
> **・**作業セッション復元
>
> **・**元画像非破壊
>
> **・**最小権限設計

## 31.2 実測後に決定

> **・**PDF最適解像度
>
> **・**JPEG圧縮品質
>
> **・**AUTOモード補正値
>
> **・**紙検出アルゴリズムの閾値
>
> **・**C++導入要否
>
> **・**PdfDocument継続使用可否

これらは机上で固定せず、実際の授業資料を使用した比較試験で決定する。
