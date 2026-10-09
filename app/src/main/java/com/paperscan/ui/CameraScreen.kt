package com.paperscan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

@Composable
fun CameraScreen(
    pageCount: Int,
    busy: Boolean,
    onCapture: (ImageCapture, onFailure: () -> Unit) -> Unit,
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
        CameraPreview(pageCount, busy, onCapture, onBack)
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
    busy: Boolean,
    onCapture: (ImageCapture, onFailure: () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var flashEnabled by remember { mutableStateOf(false) }
    var takingPicture by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, flashEnabled) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val bind = Runnable {
            runCatching {
                provider = providerFuture.get().also { it.unbindAll() }
                val preview = Preview.Builder()
                    .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder()
                    .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                    .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .build()
                provider!!.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
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

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = onBack, enabled = !takingPicture && !busy) { Text("戻る") }
                Text("$pageCount ページ", color = MaterialTheme.colorScheme.onSurface)
                OutlinedButton(
                    onClick = { flashEnabled = !flashEnabled },
                    enabled = !takingPicture && !busy,
                    modifier = Modifier.semantics { contentDescription = "フラッシュ切替" }
                ) { Text(if (flashEnabled) "フラッシュ: ON" else "フラッシュ: OFF") }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        val capture = imageCapture ?: run {
                            cameraError = "カメラを準備しています。"
                            return@Button
                        }
                        takingPicture = true
                        onCapture(capture) { takingPicture = false }
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
