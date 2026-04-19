package com.securemessenger.ui.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SecureMessengerApp
    val vm: QRViewModel = viewModel(factory = QRViewModel.Factory(app))
    val scope = rememberCoroutineScope()

    val qrBitmap = remember { vm.generateQrBitmap() }
    var scanning by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var showNicknameDialog by remember { mutableStateOf<String?>(null) }
    var showManualEntry by remember { mutableStateOf(false) }

    val finish = {
        if (!finishing) {
            finishing = true
            onDone()
        }
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted; if (granted) scanning = true }

    if (showNicknameDialog != null) {
        var nickname by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNicknameDialog = null; scanning = true },
            title = { Text("Add Contact") },
            text = {
                Column {
                    Text("Enter a nickname for this contact:")
                    TextField(value = nickname, onValueChange = { nickname = it })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val json = showNicknameDialog!!
                    showNicknameDialog = null
                    scope.launch {
                        if (!finishing) {
                            val success = vm.addContactFromJson(json, nickname.takeIf { it.isNotBlank() })
                            if (success) {
                                finish()
                            } else {
                                scanning = true
                            }
                        }
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = null; scanning = true }) { Text("Cancel") }
            }
        )
    }

    if (showManualEntry) {
        var manualText by remember { mutableStateOf("") }
        var inputError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showManualEntry = false },
            title = { Text("Enter Contact Code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste the contact's key code below.")
                    OutlinedTextField(
                        value = manualText,
                        onValueChange = { manualText = it; inputError = false },
                        label = { Text("Key code") },
                        minLines = 3,
                        maxLines = 6,
                        isError = inputError,
                        supportingText = if (inputError) {
                            { Text("Invalid code — check that it was copied completely.") }
                        } else null,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = manualText.trim()
                        if (vm.isValidContactJson(trimmed)) {
                            showManualEntry = false
                            showNicknameDialog = trimmed
                        } else {
                            inputError = true
                        }
                    },
                    enabled = manualText.isNotBlank(),
                ) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showManualEntry = false }) { Text("Cancel") }
            },
        )
    }

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
            var copied by remember { mutableStateOf(false) }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Contact code", vm.ownQrJson))
                copied = true
            }) {
                Text(if (copied) "Copied!" else "Copy my code")
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (hasCameraPermission) scanning = true
                else permissionLauncher.launch(Manifest.permission.CAMERA)
            }) {
                Text("Scan Contact's QR")
            }
            TextButton(onClick = { showManualEntry = true }) {
                Text("Enter code manually")
            }
            if (scanning) {
                CameraPreview(
                    modifier = Modifier.fillMaxWidth().height(240.dp).padding(top = 16.dp),
                    onQrDecoded = { json ->
                        scanning = false
                        showNicknameDialog = json
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = finish) { Text("Done") }
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
