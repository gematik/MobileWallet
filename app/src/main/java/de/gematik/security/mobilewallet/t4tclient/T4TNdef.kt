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

package de.gematik.security.mobilewallet.t4tclient

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import de.gematik.security.credentialExchangeLib.extensions.toHex
import java.math.BigInteger
import java.nio.ByteBuffer

class T4TNdef(tag: Tag) {

    private val tag = T4TNdef::class.java.name

    val ok = byteArrayOf(
        0x90.toByte(), //SW1
        0x00.toByte()  //SW2
    )

    val ndefTagAppAid = byteArrayOf(
        0xD2.toByte(), 0x76.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x85.toByte(), 0x01.toByte(), 0x01.toByte()
    )
    val ccFileId = byteArrayOf(0xE1.toByte(), 0x03.toByte())

    val isoDep: IsoDep = IsoDep.get(tag)

    suspend fun getNdefMessage(): NdefMessage? {
        return runCatching {
            // connect
            isoDep.connect()
            isoDep.timeout = 5000
            // select NDEF TAG application
            selectApp(ndefTagAppAid)
            // select capability container file
            selectFile(ccFileId)
            // read NDEF File ID from capability container file
            val ndefFileId = readBinary(9, 2)
            // select NDEF File
            selectFile(ndefFileId)
            // read NDEF file length from file offset 0 (NDEF message length + 2)
            val ndefFileLength = BigInteger(1, readBinary(0, 2)).toInt() + 2
            // read NDEF message from file offset 2 to end of file
            NdefMessage(ByteBuffer.wrap(ByteArray(ndefFileLength - 2)).apply {
                var offset = 2
                while (offset < ndefFileLength - 255) {
                    put(readBinary(offset, 255))
                    offset += 255
                }
                put(readBinary(offset, ndefFileLength - offset))
            }.array())
        }.onFailure { Log.e(tag, "failure reading tag: ${it.message}") }.getOrNull()
    }

    private fun selectApp(aid: ByteArray) {
        check(aid.size == 7) { "incorrect aid size: expected 7 but was ${aid.size}" }
        (byteArrayOf(
            // select APP
            0x00.toByte(),
            0xA4.toByte(),
            0x04.toByte(),
            0x00.toByte(),
            0x07.toByte()
        ) + aid + 0x00.toByte()
                ).let { commandApdu ->
                isoDep.transceive(commandApdu).let {
                    Log.d(tag, "sent: ${commandApdu.toHex()}")
                    check(it.contentEquals(ok))
                    Log.d(tag, "received: ${it.toHex()}")
                }
            }
    }

    private fun selectFile(fid: ByteArray) {
        check(fid.size == 2) { "incorrect fid size: expected 2 but was ${fid.size}" }
        (byteArrayOf(
            // select CC file
            0x00.toByte(),
            0xA4.toByte(),
            0x00.toByte(),
            0x0C.toByte(),
            0x02.toByte(),
        ) + fid).let { commandApdu ->
            isoDep.transceive(commandApdu).let {
                Log.d(tag, "sent: ${commandApdu.toHex()}")
                check(it.contentEquals(ok))
                Log.d(tag, "received: ${it.toHex()}")
            }
        }

    }

    private fun readBinary(offset: Int, length: Int): ByteArray {
        check(offset < 0xffff) { "invalid offset: $offset" }
        check(length <= 255) { "invalid length: $length" }
        return (byteArrayOf(
            0x00.toByte(), // CLA CLASS
            0xB0.toByte(), // INS READ_BINARY
        ) +
                offset.shr(8).toByte() +
                (offset % 0x100).toByte() +
                length.toByte()
                ).let { commandApdu ->
                isoDep.transceive(commandApdu).let {
                    Log.d(tag, "sent: ${commandApdu.toHex()}")
                    Log.d(tag, "received: ${it.toHex()}")
                    it.sliceArray(it.size - 2..it.size - 1).let {
                        check(it.contentEquals(ok)) { "unexpected response: ${it.toHex()}" }
                    }
                    it.sliceArray(0..it.size - 3)
                }
            }
    }
}