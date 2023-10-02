package de.gematik.security.mobilewallet

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import de.gematik.security.credentialExchangeLib.connection.Invitation

val Invitation.url: String
    get() = "https://my-wallet.me/ssi?oob=${this.toBase64()}"

val Invitation.qrCode: Bitmap
    get() =
        QRCodeWriter().encode(
            this.url,
            BarcodeFormat.QR_CODE,
            256,
            256
        ).let {
            Bitmap.createBitmap(it.width, it.height, Bitmap.Config.RGB_565).apply {
                for (x in 0 until it.width) {
                    for (y in 0 until it.height) {
                        setPixel(x, y, if (it[x, y]) Color.BLACK else Color.WHITE)
                    }
                }
            }
        }
