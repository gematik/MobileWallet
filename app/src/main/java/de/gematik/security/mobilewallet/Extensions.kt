/*
 * Copyright 2021-2024, gematik GmbH
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

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
