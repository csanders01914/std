package com.securemessenger.ui.qr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

class QRAnalyzer(private val onResult: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader()

    override fun analyze(image: ImageProxy) {
        try {
            val buffer: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val source = PlanarYUVLuminanceSource(
                bytes,
                image.width, image.height,
                0, 0, image.width, image.height,
                false,
            )
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            onResult(result.text)
        } catch (_: Exception) {
            // No QR code found in this frame — continue scanning
        } finally {
            image.close()
        }
    }
}
