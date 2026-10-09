# PaperScan 詳細設計書 v0.1

**Android向け 紙資料撮影・補正・PDF化アプリ**  
作成日: 2026-10-07  
上位文書: `PaperScan_Requirements_v0.2` / `PaperScan_Basic_Design_v0.1`  
技術方針: Kotlin主体。C++ / NDK / JNIは実測上の必要性が生じた場合のみ追加する。

---

## 0. 文書の位置づけ

本書は、要件定義書 v0.2 と基本設計書 v0.1 を実装単位へ落とし込むための詳細設計である。

本書で定める内容は次のとおり。

- Androidプロジェクトのパッケージ構成
- 画面遷移とUI状態
- Kotlinデータモデル
- クラスごとの責務・公開メソッド・依存関係
- 作業セッションの永続化形式
- CameraX / Photo Picker / OpenCV / PdfDocument / SAF / Android共有の接続方法
- 紙検出・射影補正・画質補正の処理手順
- メモリ・ネイティブメモリ・ストレージの解放規則
- エラー処理、復旧、異常終了時の扱い
- Ver.0.1からVer.1.0までの実装境界

詳細設計であっても、将来必要になるかもしれない機能を先回りして抽象化しない。実装時に必要な責務だけを分離する。

---

# 1. 設計上の固定方針

## 1.1 アプリの責務

PaperScanの責務は次の一文に限定する。

> Android端末上で紙資料を取り込み、紙面を読みやすく補正し、複数ページPDFとして保存・共有する。

次の責務は持たない。

- アカウント
- クラウド同期
- Google Drive API連携
- OCR
- AI要約
- 授業・科目管理
- PDFライブラリ管理機能
- 文書検索

## 1.2 実装優先順位

1. 情報を失わないこと
2. クラッシュしないこと
3. PDFとして読みやすいこと
4. 操作が単純であること
5. 処理時間
6. 機能数

## 1.3 処理単位

フル解像度画像の処理は常に **1ページずつ** 行う。

全ページ分の `Bitmap` や OpenCV `Mat` を同時保持しない。

## 1.4 セッション数

初期版では **同時に1つの作業セッションのみ** を扱う。

複数案件の保存・履歴・セッション一覧は実装しない。

---

# 2. Androidプロジェクト構成

## 2.1 ルートパッケージ

```text
com.paperscan
```

アプリIDは実装開始時に最終確定する。名称変更が発生しても、機能設計には影響させない。

## 2.2 パッケージ構成

```text
com.paperscan
│
├─ MainActivity.kt
├─ PaperScanApp.kt
├─ AppContainer.kt
│
├─ ui/
│  ├─ navigation/
│  │  ├─ PaperScanNavHost.kt
│  │  └─ Routes.kt
│  ├─ home/
│  │  └─ HomeScreen.kt
│  ├─ camera/
│  │  └─ CameraScreen.kt
│  ├─ session/
│  │  └─ SessionScreen.kt
│  ├─ edit/
│  │  ├─ PageEditScreen.kt
│  │  └─ CornerOverlay.kt
│  ├─ export/
│  │  ├─ ExportScreen.kt
│  │  └─ CompleteScreen.kt
│  └─ common/
│     ├─ LoadingOverlay.kt
│     └─ ErrorDialog.kt
│
├─ model/
│  ├─ ScanSession.kt
│  ├─ PageItem.kt
│  ├─ CornerPoints.kt
│  ├─ ScanMode.kt
│  ├─ PageStatus.kt
│  ├─ SourceType.kt
│  └─ DetectionConfidence.kt
│
├─ viewmodel/
│  ├─ ScanSessionViewModel.kt
│  ├─ ScanSessionUiState.kt
│  └─ ExportUiState.kt
│
├─ camera/
│  └─ CameraCaptureService.kt
│
├─ import/
│  └─ PhotoImportService.kt
│
├─ image/
│  ├─ DocumentProcessor.kt
│  ├─ OpenCvDocumentProcessor.kt
│  ├─ SourceImageLoader.kt
│  ├─ DetectionResult.kt
│  └─ ProcessedPage.kt
│
├─ pdf/
│  ├─ PdfExporter.kt
│  ├─ PdfPageSizer.kt
│  └─ ExportResult.kt
│
├─ storage/
│  ├─ WorkingFileStore.kt
│  ├─ SessionStore.kt
│  └─ StorageSpaceChecker.kt
│
└─ share/
   └─ ShareService.kt
```

この構成以上の層分けは、具体的な必要性が出るまで行わない。

---

# 3. 依存関係

## 3.1 主要依存

- Kotlin
- Jetpack Compose
- Navigation Compose
- AndroidX Lifecycle ViewModel
- Kotlin Coroutines
- CameraX
- Android Photo Picker / Activity Result API
- AndroidX ExifInterface
- OpenCV Android AAR
- Android `PdfDocument`
- Android Storage Access Framework
- AndroidX `FileProvider`

## 3.2 導入しない依存

初期実装では以下を導入しない。

- Hilt / Dagger / Koin
- Room
- Retrofit
- WorkManager
- Foreground Service
- RxJava
- 独自PDFライブラリ
- 画像ロードライブラリ

WorkManagerやForeground Serviceは、PDF生成中のバックグラウンド遷移が実機試験で問題になった場合のみ再検討する。

---

# 4. 依存生成

## 4.1 AppContainer

DIフレームワークは使用せず、`AppContainer` で必要なオブジェクトを1回だけ生成する。

```kotlin
class AppContainer(context: Context) {
    val workingFileStore: WorkingFileStore
    val sessionStore: SessionStore
    val photoImportService: PhotoImportService
    val sourceImageLoader: SourceImageLoader
    val documentProcessor: DocumentProcessor
    val pdfExporter: PdfExporter
    val shareService: ShareService
}
```

## 4.2 依存方向

```text
Compose Screen
      ↓
ScanSessionViewModel
      ↓
サービス / ストレージクラス
      ↓
Android API / OpenCV
```

UIからOpenCVやファイルAPIを直接呼ばない。

---

# 5. 画面遷移

## 5.1 ルート

```text
home
camera
session
edit/{pageId}
export
complete
```

## 5.2 通常フロー

```text
Home
 ├─ 撮影する ──────────> Camera
 │                        ↓
 │                    Session
 │
 └─ 画像から追加 ───────> Photo Picker
                          ↓
                       Session
                          ↓
                     Page Edit
                          ↓
                       Session
                          ↓
                        Export
                          ↓
                 ACTION_CREATE_DOCUMENT
                          ↓
                       Complete
```

## 5.3 戻る操作

- `Camera` → `Home` または既存セッションがあれば `Session`
- `Page Edit` → `Session`
- `Export` 生成開始前 → `Session`
- PDF生成中 → 原則として画面操作を無効化
- `Complete` → `Home`

セッションにページが存在する状態でHomeへ戻る場合、破棄確認を表示する。

---

# 6. 起動時処理

`MainActivity` 起動時に `ScanSessionViewModel.initialize()` を1回呼ぶ。

処理順:

```text
アプリ起動
  ↓
WorkingFileStore.cleanupStaleCache()
  ↓
SessionStore.load()
  ├─ sessionなし → Home通常表示
  ├─ 正常session → 「続きから / 破棄」を表示
  └─ 壊れたsession → 復旧不能メッセージ + 破棄選択
```

作業セッションを自動で破棄しない。

---

# 7. データモデル

## 7.1 NormalizedPoint

```kotlin
data class NormalizedPoint(
    val x: Float,
    val y: Float
)
```

制約:

\[
0 \le x \le 1, \qquad 0 \le y \le 1
\]

値は画像の実ピクセル座標ではなく、画像サイズに対する正規化座標とする。

## 7.2 CornerPoints

```kotlin
data class CornerPoints(
    val topLeft: NormalizedPoint,
    val topRight: NormalizedPoint,
    val bottomRight: NormalizedPoint,
    val bottomLeft: NormalizedPoint
)
```

順番は常に `TL → TR → BR → BL` とする。

## 7.3 ScanMode

```kotlin
enum class ScanMode {
    AUTO,
    TEXT,
    COLOR
}
```

## 7.4 SourceType

```kotlin
enum class SourceType {
    CAMERA,
    IMPORT
}
```

## 7.5 PageStatus

永続状態は次の3種類とする。

```kotlin
enum class PageStatus {
    READY,
    NEEDS_REVIEW,
    ERROR
}
```

`PROCESSING` は永続化しない。処理中状態は `UiState` 側で表現する。

## 7.6 DetectionConfidence

```kotlin
enum class DetectionConfidence {
    HIGH,
    MEDIUM,
    LOW
}
```

## 7.7 PageItem

```kotlin
data class PageItem(
    val id: String,
    val sourceType: SourceType,
    val sourceUri: String?,
    val workingRelativePath: String?,
    val thumbnailRelativePath: String?,
    val corners: CornerPoints?,
    val rotationQuarterTurns: Int,
    val scanMode: ScanMode,
    val status: PageStatus,
    val detectionConfidence: DetectionConfidence?
)
```

### 不変条件

CAMERA:

- `workingRelativePath != null`
- `sourceUri == null`

IMPORT:

- 通常は `sourceUri != null`
- URI永続権限が取得できない場合のみ `workingRelativePath` に一時コピーを持つ

`rotationQuarterTurns` は `0, 1, 2, 3` のみ。

ページ順は `ScanSession.pages` のList順を正とし、別の `order` フィールドは持たない。

## 7.8 ScanSession

```kotlin
data class ScanSession(
    val schemaVersion: Int,
    val id: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val pages: List<PageItem>,
    val selectedPageId: String?
)
```

`schemaVersion` 初期値は `1`。

---

# 8. UI状態

## 8.1 ScanSessionUiState

```kotlin
data class ScanSessionUiState(
    val initialized: Boolean = false,
    val session: ScanSession? = null,
    val busy: Boolean = false,
    val currentOperation: String? = null,
    val error: UiError? = null,
    val recoveryAvailable: Boolean = false
)
```

## 8.2 ExportUiState

```kotlin
sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Processing(val current: Int, val total: Int) : ExportUiState
    data class ReadyToSave(val tempPdfPath: String) : ExportUiState
    data class Saving(val destination: String?) : ExportUiState
    data class Completed(val savedUri: String) : ExportUiState
    data class Failed(val error: UiError, val retryable: Boolean) : ExportUiState
}
```

一時PDFの絶対パスをUIへ直接表示しない。

---

# 9. ScanSessionViewModel

## 9.1 責務

- セッションの唯一のUI状態所有者
- 画面イベント受付
- ページ追加 / 削除 / 並べ替え
- ページ補正情報更新
- SessionStoreへの保存
- PDF生成開始
- エラーをUI向け状態へ変換

OpenCV処理そのものは行わない。

## 9.2 主メソッド

```kotlin
suspend fun initialize()
fun createSessionIfNeeded()
suspend fun importImages(uris: List<Uri>)
suspend fun addCapturedPage(file: File)
suspend fun deletePage(pageId: String)
suspend fun movePage(fromIndex: Int, toIndex: Int)
suspend fun updateCorners(pageId: String, corners: CornerPoints)
suspend fun rotatePageClockwise(pageId: String)
suspend fun updateScanMode(pageId: String, mode: ScanMode)
suspend fun redetectPage(pageId: String)
suspend fun generatePdf()
suspend fun onPdfSaved(uri: Uri)
suspend fun discardSession()
fun clearError()
```

## 9.3 更新規則

変更処理は原則として次の順で行う。

```text
入力検証
 ↓
新しいScanSessionを生成
 ↓
SessionStore.saveAtomic()
 ↓
StateFlowを更新
```

永続化に失敗した場合、UIだけを成功状態に進めない。

---

# 10. SessionStore

## 10.1 保存場所

```text
filesDir/
└─ scan_session/
   └─ session.json
```

初期版は同時1セッションのため固定ディレクトリとする。

## 10.2 公開メソッド

```kotlin
fun exists(): Boolean
suspend fun load(): Result<ScanSession>
suspend fun saveAtomic(session: ScanSession): Result<Unit>
suspend fun delete(): Result<Unit>
```

## 10.3 atomic save

```text
session.json
session.json.tmp
```

保存手順:

1. `session.json.tmp` に完全なJSONを書き込む。
2. streamをcloseする。
3. JSONを最低限再読込して構文確認する。
4. 既存 `session.json` と置換する。
5. 失敗時は旧 `session.json` を保持する。

## 10.4 JSON形式

```json
{
  "schemaVersion": 1,
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "createdAtEpochMillis": 1791331200000,
  "updatedAtEpochMillis": 1791331200000,
  "selectedPageId": "page-001",
  "pages": [
    {
      "id": "page-001",
      "sourceType": "CAMERA",
      "sourceUri": null,
      "workingRelativePath": "captures/page-001.jpg",
      "thumbnailRelativePath": "thumbs/page-001.jpg",
      "rotationQuarterTurns": 0,
      "scanMode": "AUTO",
      "status": "READY",
      "detectionConfidence": "HIGH",
      "corners": {
        "topLeft": {"x": 0.08, "y": 0.07},
        "topRight": {"x": 0.92, "y": 0.08},
        "bottomRight": {"x": 0.94, "y": 0.93},
        "bottomLeft": {"x": 0.06, "y": 0.92}
      }
    }
  ]
}
```

---

# 11. WorkingFileStore

## 11.1 ディレクトリ

```text
filesDir/
└─ scan_session/
   ├─ session.json
   ├─ captures/
   └─ fallback_imports/

cacheDir/
└─ paperscan/
   ├─ thumbnails/
   ├─ previews/
   └─ export/
```

## 11.2 保存ルール

| 種類 | 保存先 | 作業途中でOS削除されてもよいか |
|---|---|---:|
| Camera撮影原稿 | `filesDir` | いいえ |
| URI永続化不可のimport fallback | `filesDir` | いいえ |
| session.json | `filesDir` | いいえ |
| thumbnail | `cacheDir` | はい |
| preview | `cacheDir` | はい |
| 完成前一時PDF | `cacheDir` | 原則いいえ。ただし消えた場合は再生成可能 |

一時PDFは再生成できるためcache扱いとするが、保存操作中に存在確認する。

## 11.3 主メソッド

```kotlin
fun createCaptureFile(pageId: String): File
fun createFallbackImportFile(pageId: String, extension: String): File
fun thumbnailFile(pageId: String): File
fun previewFile(pageId: String): File
fun exportTempFile(sessionId: String): File
suspend fun deletePageFiles(page: PageItem)
suspend fun deleteSessionFiles()
suspend fun cleanupStaleCache()
```

---

# 12. PhotoImportService

## 12.1 UI契約

画面側では `PickMultipleVisualMedia` を使用し、画像のみを選択可能にする。

選択キャンセルはエラー扱いしない。

## 12.2 import手順

```text
Photo Picker
 ↓
List<Uri>
 ↓
各URIについてread grant保持を試行
 ↓
メタデータ読込
 ↓
PageItem生成
 ↓
thumbnail生成
 ↓
SessionStore保存
```

Photo PickerのURIは、作業が端末再起動をまたぐ可能性があるため、可能な場合は `takePersistableUriPermission()` で読み取り権限を保持する。

## 12.3 永続権限失敗時

`SecurityException` 等で永続化できない場合は、対象画像のみ `fallback_imports/` へコピーする。

元画像には変更を加えない。

## 12.4 セッション終了時

保持したURI権限は、取得方法と端末挙動を確認したうえで不要分を解放する。

少なくとも新しい権限取得を無制限に蓄積しない。

---

# 13. CameraCaptureService

## 13.1 責務

- CameraX Preview / ImageCaptureの構築補助
- Camera permission確認
- 撮影ファイル作成
- `ImageCapture.takePicture()` 実行
- 成功 / 失敗をViewModelへ返す

## 13.2 保存方式

CameraX `ImageCapture` はインメモリ画像を受け取らず、`OutputFileOptions` により `filesDir/scan_session/captures/` へ直接保存する。

これにより撮影時にフル画像の追加コピーをRAMへ保持しない。

## 13.3 ファイル名

```text
page_<UUID>.jpg
```

日時だけをファイル名の一意性に使用しない。

## 13.4 撮影失敗

撮影失敗時に空ファイルまたは0 byteファイルがあれば削除する。

既存ページ状態は変更しない。

---

# 14. SourceImageLoader

## 14.1 責務

CAMERA / IMPORTの違いを吸収し、画像処理側へ入力を提供する。

## 14.2 主メソッド

```kotlin
suspend fun openInputStream(page: PageItem): InputStream
suspend fun readImageSize(page: PageItem): ImageSize
suspend fun decodePreview(page: PageItem, maxLongEdgePx: Int): Bitmap
suspend fun decodeForExport(page: PageItem, maxLongEdgePx: Int): Bitmap
suspend fun readExifRotation(page: PageItem): Int
```

## 14.3 デコード

画像ヘッダから寸法を先に取得し、必要な出力サイズに対して過剰な解像度でデコードしない。

### preview

初期候補:

```text
maxLongEdge = 1600 px
```

### export

初期候補:

```text
maxLongEdge = 3000 px
```

これらは実資料評価前の初期値であり、固定の品質基準ではない。

---

# 15. EXIF / 回転処理

## 15.1 基本順序

```text
raw decode
 ↓
EXIF orientation適用
 ↓
ユーザー指定rotationQuarterTurns適用
 ↓
紙検出 / 補正
```

## 15.2 目的

紙の四隅座標は「表示上正しい向きへ補正された画像」を基準に保持する。

EXIF未適用画像の座標を永続化しない。

---

# 16. DocumentProcessorインターフェース

C++移行時の唯一の主要境界とする。

```kotlin
interface DocumentProcessor {
    suspend fun detect(page: PageItem): DetectionResult
    suspend fun createPreview(page: PageItem): ProcessedPage
    suspend fun processForExport(
        page: PageItem,
        maxLongEdgePx: Int
    ): ProcessedPage
}
```

初期実装:

```text
DocumentProcessor
      ↓
OpenCvDocumentProcessor
      ↓
OpenCV Android Java/Kotlin API
```

将来:

```text
DocumentProcessor
      ↓
NativeDocumentProcessor
      ↓
JNI
      ↓
C++ / OpenCV
```

UI / ViewModel / PdfExporter側は変更しないことを目標にする。

---

# 17. DetectionResult

```kotlin
data class DetectionResult(
    val corners: CornerPoints?,
    val confidence: DetectionConfidence,
    val score: Float,
    val previewWidth: Int,
    val previewHeight: Int
)
```

紙を妥当に検出できなかった場合は `corners = null` とする。

誤った四隅を無理に返さない。

---

# 18. 紙検出アルゴリズム

## 18.1 preview画像で実行

フル解像度では検出しない。

処理候補:

```text
decodePreview
 ↓
EXIF補正
 ↓
Gray conversion
 ↓
Gaussian blur
 ↓
Canny edge
 ↓
必要に応じ morphology close
 ↓
findContours
 ↓
面積上位候補抽出
 ↓
approxPolyDP
 ↓
4点・convex候補抽出
 ↓
候補score計算
 ↓
最良候補を採用
```

## 18.2 初期パラメータ

以下は実装開始用の仮値であり、テスト画像で調整する。

| 項目 | 初期候補 |
|---|---:|
| Gaussian kernel | 5 x 5 |
| approx epsilon | perimeter x 0.02 |
| 最小紙面積 | preview面積の20% |
| 最大評価候補数 | 面積上位10件 |

Canny閾値は固定値のみで決めず、画像輝度に応じた自動化も比較する。

## 18.3 候補score

初期案:

\[
S = 0.55S_{area} + 0.30S_{angle} + 0.15S_{shape}
\]

- `S_area`: 画像中に占める面積の妥当性
- `S_angle`: 4頂点が極端な形状になっていないか
- `S_shape`: 凸四角形、辺長、自己交差の有無等

重みは実資料で変更可能とする。

## 18.4 信頼度

初期案:

```text
HIGH   score >= 0.80
MEDIUM score >= 0.60
LOW    score < 0.60
```

LOWの場合は `NEEDS_REVIEW` として手動確認を要求する。

---

# 19. 四隅順序正規化

OpenCVから得られた4点は、必ず次の順へ並べ直す。

```text
TL ---- TR
|        |
|        |
BL ---- BR
```

保存順は:

```text
TL → TR → BR → BL
```

誤順序は射影変換で画像反転やねじれを起こすため、`normalizeCornerOrder()` を単体テスト対象とする。

---

# 20. 手動四隅編集

## 20.1 CornerOverlay

Compose Canvas上に以下を描画する。

- 半透明の紙領域ポリゴン
- 4辺
- 4つのドラッグハンドル

## 20.2 座標変換

画面座標 `(sx, sy)` から表示画像内座標へ変換し、その後正規化する。

\[
x_n = \frac{x_i}{W_i}, \qquad y_n = \frac{y_i}{H_i}
\]

画像が `ContentScale.Fit` で余白を持つ場合、画面全体サイズではなく実表示画像矩形を基準にする。

## 20.3 制約

- ハンドルは画像外へ出さない
- ポリゴンが自己交差する配置は確定不可
- 面積が極端に小さい場合は確定不可

---

# 21. 射影補正

## 21.1 出力サイズ

四辺の長さを画像ピクセル座標へ戻して計算する。

\[
W = \max(|TL-TR|, |BL-BR|)
\]

\[
H = \max(|TL-BL|, |TR-BR|)
\]

長辺が処理上限を超える場合は縦横比を維持して縮小する。

## 21.2 OpenCV処理

```text
4 source points
 ↓
4 destination points
 ↓
getPerspectiveTransform
 ↓
warpPerspective
```

OpenCVの `getPerspectiveTransform` と `warpPerspective` を使用する。

## 21.3 補間

初期候補は線形補間とし、文字・細線での品質を実資料で確認する。

---

# 22. 画質補正

## 22.1 共通原則

- 白飛びで薄い線を消さない
- 文字の輪郭強調でノイズを増幅しすぎない
- COLORでは色情報を優先する
- AUTOでは「自然なカラー文書」を基本にする

## 22.2 AUTO

処理候補:

```text
Perspective
 ↓
軽いwhite balance / color cast correction
 ↓
局所または全体brightness補正
 ↓
contrast調整
 ↓
弱いnoise reduction
 ↓
必要な場合のみ弱いsharpen
```

## 22.3 TEXT

AUTOより背景均一化とコントラストを強くする。

完全な1bit二値化は初期版では行わない。

## 22.4 COLOR

- White balance: 弱め
- Contrast: 弱め
- Sharpen: 弱め
- Saturation: 原則変更しない

色付き回路図やグラフが意味を失わないことを優先する。

## 22.5 補正値

補正パラメータは `OpenCvDocumentProcessor` 内のprivate設定にまとめる。

ユーザー向け詳細スライダーはVer.1.0対象外。

---

# 23. OpenCVネイティブメモリ管理

OpenCV `Mat` はJava/Kotlinヒープ外のネイティブメモリを使用するため、処理終了時に明示的に `release()` する。

基本形:

```kotlin
val src = Mat()
val gray = Mat()
val edges = Mat()
try {
    // process
} finally {
    edges.release()
    gray.release()
    src.release()
}
```

ループ内で作成した `Mat` をページ跨ぎで保持しない。

---

# 24. Bitmapメモリ管理

- ViewModelにフル解像度Bitmapを保存しない
- `PageItem` にBitmapを入れない
- export処理内のローカル変数としてのみ保持
- 1ページ終了後は参照を残さない
- Thumbnailのみ小サイズでUIに保持可能

安易に大量の `Bitmap.recycle()` 呼び出しへ依存せず、ライフタイムを短くする設計で管理する。

---

# 25. Thumbnail生成

## 25.1 サイズ

初期候補:

```text
長辺 320 px
JPEG quality 75
```

## 25.2 保存

```text
cacheDir/paperscan/thumbnails/<pageId>.jpg
```

サムネイルがOSに削除された場合は元画像から再生成する。

サムネイル欠損はセッション破損とは扱わない。

---

# 26. ページ並べ替え

`ScanSession.pages` のList順がPDF順となる。

## 26.1 Ver.0.1

第三者reorderライブラリを追加しない。

選択ページに対して、最低限以下を提供する。

```text
[←前へ] [後ろへ→]
```

`movePage(fromIndex, toIndex)` でList順を変更する。

## 26.2 将来

Composeだけで安全に実装できる段階でドラッグ並べ替えへ置き換えてよい。

ViewModel APIは変更しない。

---

# 27. ページ削除

処理順:

```text
削除対象確認
 ↓
新しいpages List生成
 ↓
SessionStore保存
 ↓
Camera / fallbackファイル削除
 ↓
thumbnail / preview削除
 ↓
保持URI権限を必要に応じ解放
```

セッションJSON更新前に原稿ファイルを削除しない。

---

# 28. PdfPageSizer

## 28.1 基本方針

PDF内の各ページは独立した縦横比を持てるものとする。

A4/B5/変則サイズを無理に1種類のキャンバスへ引き伸ばさない。

## 28.2 ページサイズ算出

補正済み画像の縦横比を `r = width / height` とする。

A系列比率に十分近い場合はA4相当のページ比率を使用する。

それ以外は短辺の基準値を固定し、長辺を画像比率から算出する。

画像自体はページ中央へ縦横比維持で描画し、クロップしない。

## 28.3 混在資料

A4とB5などが混在してもページごとの縦横比を維持する。

「すべてA4へ統一」はVer.1.0の標準動作にはしない。

---

# 29. PdfExporter

## 29.1 責務

- ページ順取得
- 1ページずつ画像処理
- PdfDocumentページ生成
- 画像描画
- 進捗通知
- 一時PDF出力
- エラー発生ページ特定

## 29.2 主メソッド

```kotlin
suspend fun export(
    session: ScanSession,
    onProgress: (current: Int, total: Int) -> Unit
): ExportResult
```

## 29.3 処理手順

```text
export temp path準備
 ↓
PdfDocument生成
 ↓
for page in session.pages
    ↓
  processForExport(page)
    ↓
  PdfPageSizerでpage size算出
    ↓
  startPage
    ↓
  Canvasへbitmap描画
    ↓
  finishPage
    ↓
  bitmap参照解放
    ↓
  progress通知
 ↓
writeTo(temp file)
 ↓
close
 ↓
ファイル存在 / size > 0確認
 ↓
ReadyToSave
```

Android `PdfDocument` は同時に複数ページへ書き込まず、`startPage → finishPage` を必ず1ページずつ完結させる。

## 29.4 描画

画像をPDFページ内に縦横比維持でfitさせる。

引き伸ばし禁止。

背景は白。

## 29.5 PdfDocument評価条件

実機で次を測定する。

- 1 / 10 / 20 / 30ページ
- peak memory
- export time
- output size
- 小さい文字の画質

メモリ増加が実用上問題になる場合のみ別PDFライブラリを比較する。

---

# 30. ExportResult

```kotlin
sealed interface ExportResult {
    data class Success(
        val tempFile: File,
        val pageCount: Int,
        val sizeBytes: Long
    ) : ExportResult

    data class Failure(
        val pageIndex: Int?,
        val cause: Throwable
    ) : ExportResult
}
```

ユーザー画面へThrowable本文をそのまま出さない。

---

# 31. PDF生成失敗

失敗時:

- セッションを残す
- Camera原稿を残す
- imported URIを残す
- partial temp PDFは削除する
- ページ番号が特定できる場合は表示する

例:

```text
12ページ目の処理中にPDF生成に失敗しました。
作業データは保持されています。
```

---

# 32. バックグラウンド遷移

初期版ではPDF生成をWorkManager / Foreground Serviceへ移さない。

PDF生成Jobはアプリプロセス内で実行する。

## 32.1 Activityが一時的にbackgroundへ移動

プロセスが生存している限り処理継続を許容する。

## 32.2 OSにprocess killされた場合

- 生成Jobは終了
- 一時PDFは次回起動時に破棄可能
- `filesDir` のセッションと原稿は保持
- 次回起動でユーザーがPDF生成を再実行する

この動作で実用上問題が確認された場合のみWorkManager / Foreground Serviceを検討する。

---

# 33. StorageSpaceChecker

## 33.1 目的

PDF生成開始前に明らかな空き容量不足を検出する。

## 33.2 方針

厳密な将来サイズ予測は行わない。

最低限、cache/output先の利用可能容量を確認する。

画像総量やページ数から安全マージンを含む概算を行ってもよいが、推定値を保証値として扱わない。

空き容量が極端に少ない場合のみ開始を拒否する。

---

# 34. SAF保存

## 34.1 起動

`ActivityResultContracts.CreateDocument("application/pdf")` を第一候補とする。

初期ファイル名例:

```text
PaperScan_2026-10-07.pdf
```

ユーザーが変更可能。

## 34.2 保存処理

```text
CreateDocument result Uri
 ↓
temp PDF existence check
 ↓
ContentResolver.openOutputStream(uri, "w")
 ↓
temp PDFをstream copy
 ↓
flush / close
 ↓
成功
```

## 34.3 保存失敗

- 一時PDFを残す
- 作業セッションを残す
- 再保存ボタンを表示する
- destinationに部分ファイルが残る可能性があるため、可能な場合のみbest-effort削除を試みる
- 削除失敗を理由に元セッションを破棄しない

---

# 35. 保存成功後の扱い

保存成功時点でPDFデータ自体は安全な保存先に存在する。

処理順:

```text
保存成功
 ↓
Complete画面へ
 ↓
SessionStore削除
 ↓
Camera / fallback原稿削除
 ↓
thumbnail / preview削除
 ↓
一時PDFは共有用としてComplete画面中のみ保持
```

Complete画面終了後または次回起動時に一時PDFを削除する。

---

# 36. ShareService

## 36.1 共有元

共有には、完成画面に残しているcache内一時PDFを使用する。

`FileProvider` で `content://` URIへ変換する。

## 36.2 Intent

```text
ACTION_SEND
MIME: application/pdf
EXTRA_STREAM: content Uri
FLAG_GRANT_READ_URI_PERMISSION
```

共有先はAndroid chooserに任せる。

アプリ内にGoogle Drive専用コードは持たない。

## 36.3 FileProvider公開範囲

FileProviderで公開可能なパスは、共有用export cacheディレクトリのみに限定する。

`filesDir` 全体やセッション原稿ディレクトリを外部公開しない。

---

# 37. セッション破棄

ユーザーが明示的に「破棄」を選択した場合のみ実行する。

順序:

```text
確認ダイアログ
 ↓
SessionStore読込
 ↓
作業画像 / fallback削除
 ↓
cache削除
 ↓
URI grant解放可能なら解放
 ↓
session.json削除
 ↓
Home
```

途中削除失敗はログへ残し、次回 `cleanup` で再試行する。

---

# 38. エラー分類

```kotlin
enum class ErrorCode {
    CAMERA_PERMISSION,
    CAMERA_CAPTURE,
    IMPORT_READ,
    IMPORT_PERMISSION,
    SESSION_READ,
    SESSION_WRITE,
    IMAGE_DECODE,
    DOCUMENT_DETECTION,
    IMAGE_PROCESSING,
    OUT_OF_STORAGE,
    PDF_GENERATION,
    FINAL_SAVE,
    SHARE,
    UNKNOWN
}
```

UIではコードに対応する日本語メッセージを表示する。

---

# 39. エラーメッセージ規則

悪い例:

```text
java.io.FileNotFoundException: content://...
```

良い例:

```text
画像を読み込めませんでした。
この画像へのアクセス権が失われた可能性があります。
```

必要な場合のみ「再選択」「再試行」「ページ削除」の行動を提示する。

---

# 40. ログ

デバッグログに以下を残してよい。

- session id
- page id
- 処理時間
- 画像寸法
- 検出score
- エラー種別

以下は原則としてログに残さない。

- 画像内容
- 完全な外部URI
- ユーザーが付けた最終ファイル名
- PDF内容

---

# 41. Coroutine / Thread設計

## 41.1 Main

- Compose UI更新
- Navigation
- Dialog表示

## 41.2 Dispatchers.IO

- JSON読書き
- InputStream / OutputStream
- file copy
- thumbnail保存
- SAF保存

## 41.3 Dispatchers.Default

- OpenCV画像処理
- 座標計算
- PDF用画像変換

同一ページ処理内で必要に応じ `withContext` を使い分ける。

---

# 42. 同時実行制御

次の処理は同時実行を禁止する。

- 同一ページの再検出と削除
- PDF生成の二重起動
- PDF生成中のページ並べ替え
- 保存中のセッション破棄

ViewModelの `busy` / `ExportUiState` でボタンを無効化する。

大規模なロック機構は導入しない。

---

# 43. 画面別詳細

## 43.1 HomeScreen

表示:

- アプリ名
- 撮影する
- 画像から追加
- 復旧セッションがある場合のみ「続きから」「破棄」

イベント:

```kotlin
onCameraClick()
onImportClick()
onResumeSession()
onDiscardSession()
```

## 43.2 CameraScreen

表示:

- Camera preview
- shutter
- flash mode
- page count
- 戻る

撮影成功後:

1. `addCapturedPage`
2. 簡易paper detection
3. Sessionへ遷移

連続撮影UIはVer.1.0までに検討するが、最初は1枚撮影後にSessionへ戻ってよい。

## 43.3 SessionScreen

表示:

- thumbnails
- selected preview
- 選択ページ番号 / 総ページ数
- 編集
- 削除
- 前へ / 後ろへ
- 追加撮影
- 画像追加
- PDF作成

`NEEDS_REVIEW` のページには警告マークを表示する。

## 43.4 PageEditScreen

表示:

- preview
- CornerOverlay
- 自動再検出
- 90°回転
- AUTO / TEXT / COLOR
- 適用

適用時のみsessionへ確定保存する。

## 43.5 ExportScreen

生成前:

- page count
- filename初期候補
- PDF作成

生成中:

```text
PDFを作成しています
12 / 28
```

生成後:

- 保存先を選ぶ

## 43.6 CompleteScreen

- 保存成功
- 共有
- ホームへ

---

# 44. Page Edit preview

編集画面ではフル解像度でリアルタイム処理しない。

`createPreview()` で縮小版を生成する。

四隅のドラッグ中は射影補正を連続実行せず、ポリゴン表示だけ更新する。

ユーザーが指を離した時、または「適用」時に補正previewを更新する。

---

# 45. 画像処理の再現性

同一入力・同一corners・同一modeに対し、preview処理とexport処理は同じ補正ロジックを使用する。

解像度だけを変える。

```text
preview = pipeline(input, maxLongEdge=1600)
export  = pipeline(input, maxLongEdge=3000)
```

「プレビューでは綺麗だがPDFでは別処理になる」状態を避ける。

---

# 46. 画質パラメータ管理

パラメータをUIファイルへ散在させない。

例:

```kotlin
private data class ProcessingProfile(
    val contrast: Double,
    val denoiseStrength: Double,
    val sharpenStrength: Double,
    val backgroundNormalization: Double
)
```

`ScanMode` からprofileを選択する。

値は実資料による比較試験で決定する。

---

# 47. 実資料による画質試験セット

最低限、次を含む固定テスト資料セットを用意する。

1. 黒文字中心のA4資料
2. 数式と上下付き文字
3. 電気回路図
4. 細線グラフ
5. カラー配線図
6. 赤ペン書き込み
7. 鉛筆または薄い印刷
8. 机の木目が強い背景
9. 暗い室内
10. 斜め撮影
11. 紙の一部に影
12. B5等のA4以外

実物資料をGitHub公開する場合は著作権や個人情報に注意し、必要なら自作fixtureへ置き換える。

---

# 48. 単体テスト対象

Android実機不要でテストできるロジックを優先して切り出す。

- NormalizedPoint validation
- CornerPoints ordering
- polygon self-intersection
- pixel ↔ normalized coordinate conversion
- page reorder
- page delete
- PdfPageSizer
- ScanMode profile selection
- session JSON round-trip
- schemaVersion handling
- error mapping

---

# 49. 画像処理テスト

OpenCV fixtureを使用して次を確認する。

- 四隅検出結果
- 検出失敗が適切に失敗扱いになる
- 手動cornersによる射影補正
- 出力寸法
- rotation
- AUTO/TEXT/COLORで色情報が意図どおり変化する

画像の完全なpixel一致を主判定にせず、座標誤差や視覚比較も使用する。

---

# 50. Emulatorテスト

- Navigation
- Photo Picker呼び出し
- Session画面
- reorder
- JSON復元
- CreateDocumentフロー
- エラーダイアログ

Camera画質・性能判定はEmulator結果だけで合格にしない。

---

# 51. 実機テスト

必須確認:

- CameraX撮影
- 端末の実カメラEXIF
- 20〜30ページ
- OOM有無
- OpenCVネイティブメモリ増加
- Photo Picker URI復元
- 端末再起動後のsession復元
- SAF保存
- Google Drive等のDocumentsProvider保存
- Android共有
- background遷移
- 画面OFF / 復帰

---

# 52. 性能計測

現段階では「1ページ何秒以内」という架空の固定値を受け入れ条件にしない。

まず以下を記録する。

| ページ数 | 記録項目 |
|---:|---|
| 1 | 処理時間 / peak memory / PDF size |
| 10 | 同上 |
| 20 | 同上 |
| 30 | 同上 |

さらに1ページごとの処理時間分布を確認し、異常に遅い処理を特定する。

ユーザー本人の実用上の待ち時間評価と実測値から、必要なら後から性能目標を設定する。

---

# 53. ストレージ計測

計測項目:

```text
原写真合計サイズ
session作業中filesDir使用量
cache最大使用量
完成PDFサイズ
完了後残存作業ファイルサイズ
```

完了後残存作業ファイルは原則0とする。ただしOS管理キャッシュやログは除く。

---

# 54. 異常終了試験

意図的に次のタイミングでアプリを終了し、復旧を確認する。

- import直後
- Camera撮影直後
- page reorder直後
- Page Edit保存直後
- PDF生成途中
- PDF生成完了・保存前
- SAF保存途中

PDF生成途中に終了した場合でも元セッションが失われないことを確認する。

---

# 55. セッションschema変更

初期版では複雑なmigration frameworkを作らない。

`schemaVersion` が対応外の場合:

```text
この作業データは現在のバージョンでは読み込めません。
```

とし、勝手に破壊的変換しない。

アプリが実用化され、schema変更が実際に必要になった時点でmigrationを追加する。

---

# 56. C++ / NDK追加判定

次のEvidenceがある場合のみIssue化する。

- profilerでOpenCV処理が支配的ボトルネック
- Kotlin/Java API側の不要コピーが主要原因
- 30ページ処理が実用上許容できない
- native APIでしか利用できない必要機能がある

C++導入時もUIやセッション設計を変更しない。

---

# 57. セキュリティ・プライバシー

- `INTERNET` permissionは初期版では要求しない
- `MANAGE_EXTERNAL_STORAGE` は要求しない
- 広範な写真アクセス権限を要求しない
- PaperScanが資料画像を外部送信しない
- FileProvider公開範囲をexport cacheへ限定する
- imported元画像を削除しない
- Camera撮影画像はアプリ専用領域から外へ自動公開しない

---

# 58. Android権限

初期Manifestで必要な実権限は原則:

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

Photo Picker / SAFを使用し、一般的な外部ストレージ全体のread/write権限を追加しない。

---

# 59. Ver.0.1 詳細実装範囲

Ver.0.1では画像補正をまだ実装しない。

## Scope

- Android Studioプロジェクト作成
- Compose Home / Session / Export画面
- Photo Pickerで複数画像選択
- `PageItem` / `ScanSession`
- SessionStore
- Thumbnail
- ページ削除
- 前後移動による並べ替え
- 画像を縦横比維持でPdfDocumentへ追加
- cacheへ一時PDF生成
- CreateDocumentで保存
- 保存失敗時の再試行

## Non-goals

- CameraX
- OpenCV
- 紙自動検出
- 手動四隅
- 射影補正
- AUTO/TEXT/COLOR
- C++
- WorkManager

---

# 60. Ver.0.1 受け入れ条件

1. 2枚以上の画像をPhoto Pickerで選択できる。
2. 選択画像がページ一覧へ表示される。
3. ページ削除ができる。
4. ページ順を変更できる。
5. 変更後の順番どおりPDFになる。
6. 画像が不自然に引き伸ばされない。
7. `ACTION_CREATE_DOCUMENT` 相当の保存UIでPDFを保存できる。
8. 保存失敗時に作業セッションが残る。
9. アプリ再起動後に作業セッションを復元できる。
10. import元画像を変更・削除しない。
11. 20ページ程度で基本フローがクラッシュしない。

---

# 61. Ver.0.2

- Session操作の安定化
- 復旧処理
- cache cleanup
- エラーUI改善
- ページ管理の実機検証

---

# 62. Ver.0.3

- PageEditScreen
- 手動四隅
- 座標変換
- 射影補正
- OpenCV導入

自動検出より先に「ユーザーが四隅を指定すれば必ず補正できる」経路を完成させる。

---

# 63. Ver.0.4

- 自動紙検出
- DetectionConfidence
- NEEDS_REVIEW
- 再検出
- fixture評価

---

# 64. Ver.0.5

- AUTO補正
- TEXT補正
- COLOR補正
- 画質比較
- 出力サイズ調整

---

# 65. Ver.0.6

- CameraX
- Camera permission
- App-private直接保存
- 連続利用の実機確認

---

# 66. Ver.0.7

- 20〜30ページ性能調整
- メモリプロファイル
- ストレージプロファイル
- background挙動確認
- 必要ならPDF方式の比較

---

# 67. Ver.1.0 完成条件

要件定義書 v0.2 の受け入れ条件を満たし、さらに次を確認する。

- 実際の授業プリントで使用できる
- 紙検出失敗時に手動修正で完遂できる
- 数式・添字・回路図・細線が読める
- カラー情報が必要な資料をCOLORで保持できる
- 30ページ程度をページ単位処理できる
- 一時ファイルが完了後に残り続けない
- 元画像を削除しない
- PDF生成または保存失敗から再試行できる

---

# 68. 実装開始時のIssue分割

詳細設計完成後、実装はGitHub Issue単位で進める。

最初のIssue候補:

```text
Issue 1: Android project skeleton + navigation
Issue 2: Photo Picker + session model + persistence
Issue 3: Session page list + delete + reorder
Issue 4: PdfDocument export + SAF save
Issue 5: Ver.0.1 integration / device validation
```

各Issueには最低限以下を含める。

- Purpose / Background
- Scope
- Non-goals
- Acceptance Criteria
- Validation / Test Plan

無関係なリファクタを同一Issueへ混ぜない。

---

# 69. 実装時の検証レベル

次を混同しない。

```text
コード作成済み
≠ Build成功
≠ Unit Test PASS
≠ Emulator確認済み
≠ Android実機確認済み
≠ 実授業資料で画質確認済み
```

最終的なPaperScanの品質判定は実機・実資料まで完了して初めて行う。

---

# 70. 未確定パラメータ

以下は詳細設計時点でも意図的に固定しない。

- CameraX capture mode
- Preview最大解像度の最終値
- PDF出力最大解像度の最終値
- JPEG品質
- Canny閾値
- 紙検出score重み
- confidence閾値
- AUTO / TEXT / COLOR補正値
- 1ページ目標処理時間
- C++導入要否
- PdfDocument継続可否

これらは「設計漏れ」ではなく、実測で決定する項目とする。

---

# 71. 詳細設計の受け入れ状態

本書 v0.1 の時点で、以下は実装方針確定とする。

- Single Activity + Compose
- ViewModel + StateFlow
- 手動AppContainer
- 同時1セッション
- JSON session persistence
- app-private原稿保存
- cacheと永続作業領域の分離
- Photo Picker中心のimport
- CameraXはfile direct capture
- 正規化四隅座標
- OpenCVによる紙検出・射影補正
- フル解像度1ページ処理
- OpenCV Mat明示release
- PdfDocumentを第一候補
- ページごとの縦横比維持
- SAFによる保存
- FileProvider + ACTION_SEND共有
- 保存失敗時にセッション保持
- process kill時にPDF生成を再開ではなく再実行
- C++は実測後のみ

---

# 72. 公式API確認メモ

2026-10-07時点で、実装方針に使用する主要APIについてAndroid / OpenCV公式資料を確認した。

- CameraX `ImageCapture.takePicture(OutputFileOptions, ...)` は撮影結果をファイルへ直接保存できる。
- Photo Pickerは `PickMultipleVisualMedia` を提供し、必要に応じメディアURIへの永続アクセス権を保持できる。
- Storage Access Frameworkは `ACTION_CREATE_DOCUMENT` / CreateDocument相当でユーザー指定先へ新規PDFを保存できる。
- Android `PdfDocument` は `startPage → finishPage` をページごとに行い、最後に `writeTo` してcloseする。
- `FileProvider` の `content://` URIと `FLAG_GRANT_READ_URI_PERMISSION` により共有先へ一時読み取り権限を渡せる。
- OpenCVは4点から `getPerspectiveTransform` を求め、`warpPerspective` で射影変換できる。

参考:

- Android Developers: CameraX - Capture an image
- Android Developers: Photo picker
- Android Developers: Storage Access Framework / documents and files
- Android Developers: `android.graphics.pdf.PdfDocument`
- Android Developers: `androidx.core.content.FileProvider`
- OpenCV Java documentation: `Imgproc.getPerspectiveTransform`, `Imgproc.warpPerspective`

---

# 73. 最終方針

PaperScanの詳細設計は、機能を増やすためではなく、次の事故を避けるためのものとする。

- **大量BitmapによるOOMを起こさない。**
- **作業途中の原稿をcache消失で失わない。**
- **元画像を破壊しない。**
- **補正失敗をユーザーが手動で回復できる。**
- **保存失敗とPDF生成失敗を作業消失へつなげない。**
- **実測前にC++や大規模構造を導入しない。**

この境界を維持したうえで、Ver.0.1から小さく実装・実行・検証・読解を繰り返す。
