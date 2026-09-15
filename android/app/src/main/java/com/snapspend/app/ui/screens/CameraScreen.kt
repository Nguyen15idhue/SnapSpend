package com.snapspend.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.snapspend.app.ui.theme.Spacing
import java.io.File
import java.io.FileOutputStream

// Màn thêm chi tiêu: chụp ảnh, chọn ảnh, ảnh mẫu hoặc bỏ ảnh.
// Ảnh xong sang form để OCR + AI phân loại.
@Composable
fun CameraScreen(onCaptured: (Uri?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onCaptured(uri) }

    if (!hasCamera) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Button({ permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Cho phép Camera") } }
        return
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.onSurface)) {
        androidx.compose.ui.viewinterop.AndroidView(factory = { ctx ->
            PreviewView(ctx).also { view ->
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    imageCapture = capture
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                }, ContextCompat.getMainExecutor(ctx))
            }
        }, modifier = Modifier.fillMaxSize())

        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(Spacing.s24), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.testTag("btn_gallery")) { Text("Thư viện", color = MaterialTheme.colorScheme.surface) }
                TextButton(onClick = { onCaptured(createSampleImage(context)) }, modifier = Modifier.testTag("btn_sample")) { Text("Ảnh mẫu", color = MaterialTheme.colorScheme.surface) }
            }
            IconButton(onClick = {
                val output = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                val options = ImageCapture.OutputFileOptions.Builder(output).build()
                imageCapture?.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {}
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        onCaptured(Uri.fromFile(output))
                    }
                })
            }, modifier = Modifier.size(78.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).testTag("btn_capture")) {
                Text("●", color = MaterialTheme.colorScheme.onSurface)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = { onCaptured(null) }, modifier = Modifier.testTag("btn_no_image")) { Text("Không ảnh", color = MaterialTheme.colorScheme.surface) }
                Spacer(Modifier.width(70.dp))
            }
        }
    }
}

private fun createSampleImage(context: android.content.Context): Uri {
    val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    canvas.drawColor(AndroidColor.LTGRAY)
    val paint = Paint().apply { color = AndroidColor.DKGRAY; textSize = 48f }
    canvas.drawText("SnapSpend sample", 180f, 300f, paint)
    val f = File(context.cacheDir, "sample_${System.currentTimeMillis()}.jpg")
    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    return Uri.fromFile(f)
}
