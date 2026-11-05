package com.paperscan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Surface
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.paperscan.model.PageItem
import com.paperscan.viewmodel.ScanSessionViewModel

@Composable
fun CameraScreen(
    pageCount: Int,
    latestPage: PageItem?,
    busy: Boolean,
    viewModel: ScanSessionViewModel,
    onCapture: (ImageCapture, onAccepted: () -> Unit, onFailure: () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    LaunchedEffect(permissionGranted) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!permissionGranted) {
        PermissionRequired(onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }, onBack = onBack)
    } else {
        CameraPreview(pageCount, latestPage, busy, viewModel, onCapture, onBack)
    }
}

@Composable
private fun PermissionRequired(onRequestPermission: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text("カメラの利用を許可してください", style = MaterialTheme.typography.headlineSmall)
        Text("撮影した画像はこの端末のPaperScan作業領域にのみ保存します。")
        Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) { Text("カメラを許可") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("戻る") }
    }
}

@Composable
private fun CameraPreview(
    pageCount: Int,
    latestPage: PageItem?,
    busy: Boolean,
    viewModel: ScanSessionViewModel,
    onCapture: (ImageCapture, onAccepted: () -> Unit, onFailure: () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            // A scan needs the whole source frame, rather than a screen-filling crop.
            scaleType = PreviewView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var flashEnabled by remember { mutableStateOf(false) }
    var takingPicture by remember { mutableStateOf(false) }
    var previewReady by remember { mutableStateOf(false) }
    var targetRotation by remember { mutableIntStateOf(Surface.ROTATION_0) }
    val previewAspectRatio = when (targetRotation) {
        Surface.ROTATION_90, Surface.ROTATION_270 -> 4f / 3f
        else -> 3f / 4f
    }

    DisposableEffect(previewView) {
        val updatePreviewState = {
            if (previewView.width > 0 && previewView.height > 0) {
                targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                previewReady = previewView.viewPort != null
            }
        }
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updatePreviewState() }
        previewView.addOnLayoutChangeListener(layoutListener)
        previewView.post { updatePreviewState() }
        onDispose { previewView.removeOnLayoutChangeListener(layoutListener) }
    }

    DisposableEffect(lifecycleOwner, flashEnabled, previewReady, targetRotation) {
        if (!previewReady) {
            onDispose { }
        } else {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            var provider: ProcessCameraProvider? = null
            val bind = Runnable {
                runCatching {
                    provider = providerFuture.get().also { it.unbindAll() }
                    val resolutionSelector = ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build()
                    val preview = Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setTargetRotation(targetRotation)
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }
                    val capture = ImageCapture.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setTargetRotation(targetRotation)
                        .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                        .build()
                    val useCaseGroup = UseCaseGroup.Builder()
                        .addUseCase(preview)
                        .addUseCase(capture)
                        .setViewPort(requireNotNull(previewView.viewPort))
                        .build()
                    val camera = provider!!.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        useCaseGroup
                    )
                    // CameraX normally starts here, but make the no-zoom requirement explicit on every rebind.
                    camera.cameraControl.setZoomRatio(INITIAL_ZOOM_RATIO)
                    imageCapture = capture
                    cameraError = null
                }.onFailure {
                    imageCapture = null
                    cameraError = "カメラを開始できませんでした。"
                }
            }
            providerFuture.addListener(bind, ContextCompat.getMainExecutor(context))
            onDispose {
                imageCapture = null
                provider?.unbindAll()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .align(Alignment.Center)
                .aspectRatio(previewAspectRatio)
                .fillMaxWidth()
                .clipToBounds()
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = onBack, enabled = !takingPicture && !busy) {
                    Text(if (pageCount > 0) "ページ一覧" else "戻る")
                }
                Text("$pageCount ページ", color = MaterialTheme.colorScheme.onSurface)
                OutlinedButton(
                    onClick = { flashEnabled = !flashEnabled },
                    enabled = !takingPicture && !busy,
                    modifier = Modifier.semantics { contentDescription = "フラッシュ切替" }
                ) { Text(if (flashEnabled) "フラッシュ: ON" else "フラッシュ: OFF") }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                RecentCapturePreview(pageCount, latestPage, viewModel)
                Button(
                    onClick = {
                        val capture = imageCapture ?: run {
                            cameraError = "カメラを準備しています。"
                            return@Button
                        }
                        takingPicture = true
                        onCapture(capture, { takingPicture = false }, { takingPicture = false })
                    },
                    enabled = !takingPicture && !busy && imageCapture != null,
                    modifier = Modifier.size(104.dp).semantics {
                        contentDescription = "撮影"
                        testTag = "camera_shutter"
                    }
                ) { Text(if (takingPicture) "保存中" else "撮影") }
                if (takingPicture || busy) CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun RecentCapturePreview(
    pageCount: Int,
    page: PageItem?,
    viewModel: ScanSessionViewModel
) {
    if (page == null || pageCount == 0) return
    val thumbnail by produceState<Bitmap?>(null, page.id, page.thumbnailRelativePath) {
        value = runCatching { viewModel.loadThumbnail(page) }.getOrNull()
    }
    DisposableEffect(thumbnail) {
        onDispose { thumbnail?.takeUnless(Bitmap::isRecycled)?.recycle() }
    }
    Row(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        thumbnail?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "直近の撮影ページのサムネイル",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(56.dp)
            )
        } ?: CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Column {
            Text("直近の撮影ページ", color = MaterialTheme.colorScheme.onSurface)
            Text("${pageCount}ページ目", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val INITIAL_ZOOM_RATIO = 1.0f
