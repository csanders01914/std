package com.securemessenger.ui.qr

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.securemessenger.SecureMessengerApp
import com.securemessenger.contact.Contact
import com.securemessenger.identity.KeyBundle

class QRViewModel(private val app: SecureMessengerApp) : ViewModel() {
    private val service = app.messagingService
    val ownQrJson: String = service.ownBundle.toJson()

    fun generateQrBitmap(sizePx: Int = 512): Bitmap {
        val bits = QRCodeWriter().encode(ownQrJson, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) for (y in 0 until sizePx)
            bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    fun addContactFromJson(json: String) {
        val bundle  = KeyBundle.fromJson(json)
        val contact = Contact.fromKeyBundle(bundle)
        service.contactStore.add(contact)
    }

    class Factory(private val app: SecureMessengerApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = QRViewModel(app) as T
    }
}
