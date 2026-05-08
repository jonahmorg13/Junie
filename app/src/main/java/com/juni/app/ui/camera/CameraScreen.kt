package com.juni.app.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.juni.app.JuniApp
import com.juni.app.data.image.toUprightJpeg
import com.juni.app.ui.terminal.TermBox
import com.juni.app.ui.terminal.TermButton
import com.juni.app.ui.terminal.TermColor
import com.juni.app.ui.terminal.TermText
import androidx.concurrent.futures.await

@Composable
fun CameraScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row { TermButton(label = "back", onClick = onBack) }
            TermBox(title = "camera permission") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TermText(
                        text = "juni needs camera access to capture whiteboards and journal pages.",
                        color = TermColor.Dim,
                    )
                    TermButton(
                        label = "grant",
                        color = TermColor.Accent,
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    )
                }
            }
        }
        return
    }

    val previewView = remember { PreviewView(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var lensBackFacing by remember { mutableStateOf(true) }
    var capturing by remember { mutableStateOf(false) }

    LaunchedEffect(previewView, lensBackFacing) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
        }
        val capture = ImageCapture.Builder().build()
        runCatching {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                if (lensBackFacing) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                capture,
            )
            imageCapture = capture
        }.onFailure { Log.e(LOG_TAG, "bindToLifecycle failed", it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TermButton(label = "back", onClick = onBack)
            TermButton(
                label = if (lensBackFacing) "flip" else "back lens",
                onClick = { lensBackFacing = !lensBackFacing },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        ) {
            TermButton(
                label = if (capturing) "..." else "◉ capture",
                color = TermColor.Accent,
                enabled = !capturing && imageCapture != null,
                onClick = {
                    val cap = imageCapture ?: return@TermButton
                    capturing = true
                    cap.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                runCatching {
                                    val bytes = image.toUprightJpeg()
                                    JuniApp.get().addComposerImage(bytes)
                                }.onFailure { Log.e(LOG_TAG, "encoding failed", it) }
                                image.close()
                                capturing = false
                                onBack()
                            }

                            override fun onError(exc: ImageCaptureException) {
                                Log.e(LOG_TAG, "capture failed", exc)
                                capturing = false
                            }
                        },
                    )
                },
            )
        }
    }
}

private const val LOG_TAG = "juni-camera"
