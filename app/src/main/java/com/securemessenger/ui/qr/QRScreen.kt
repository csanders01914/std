package com.securemessenger.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securemessenger.SecureMessengerApp
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SecureMessengerApp
    val vm: QRViewModel = viewModel(factory = QRViewModel.Factory(app))

    val qrBitmap = remember { vm.generateQrBitmap() }
    var scanning by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted; if (granted) scanning = true }

    Scaffold(topBar = { TopAppBar(title = { Text("Exchange Keys") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Your QR code", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "Your identity QR code",
                modifier = Modifier.size(280.dp).padding(8.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                if (hasCameraPermission) scanning = true
                else permissionLauncher.launch(Manifest.permission.CAMERA)
            }) {
                Text("Scan Contact's QR")
            }
            if (scanning) {
                CameraPreview(
                    modifier = Modifier.fillMaxWidth().height(240.dp).padding(top = 16.dp),
                    onQrDecoded = { json ->
                        scanning = false
                        vm.addContactFromJson(json)
                        onDone()
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
private fun CameraPreview(modifier: Modifier, onQrDecoded: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var decoded by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { ia ->
                        ia.setAnalyzer(executor, QRAnalyzer { result ->
                            if (!decoded) {
                                decoded = true
                                onQrDecoded(result)
                            }
                        })
                    }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
