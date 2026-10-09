package com.paperscan.camera

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import com.paperscan.storage.WorkingFileStore
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor

/**
 * Keeps CameraX file output in the app-private session directory.  It never
 * requests an in-memory image, so a capture does not add a full-size Bitmap to
 * the UI process.
 */
class CameraCaptureService(
    private val workingFileStore: WorkingFileStore,
    private val callbackExecutor: Executor
) {
    fun capture(
        imageCapture: ImageCapture,
        onSaved: (File) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val file = workingFileStore.captureFile(UUID.randomUUID().toString())
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(options, callbackExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                if (file.length() <= 0L) {
                    file.delete()
                    onFailure("撮影画像を保存できませんでした。")
                } else {
                    onSaved(file)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                onFailure("撮影に失敗しました。もう一度お試しください。")
            }
        })
    }
}
