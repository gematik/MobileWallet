package de.gematik.security.mobilewallet

import android.util.Log
import de.gematik.security.credentialExchangeLib.connection.DidCommV2OverHttp.createPeerDID
import de.gematik.security.credentialExchangeLib.crypto.KeyPair
import de.gematik.security.credentialExchangeLib.crypto.ecdsa.P256CryptoCredentials
import de.gematik.security.credentialExchangeLib.extensions.hexToByteArray
import de.gematik.security.mobilewallet.crypto.BiometricCryptoCredentials
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.util.*

object Settings {
    private val tag = Settings::class.java.name

    val wsServerPort = 9090

    val localEndpoint = URI("ws", null, "0.0.0.0", wsServerPort, "/ws", null, null)

    val ownInternetAddress = NetworkInterface.getNetworkInterfaces()
        .toList().first { it.name.lowercase().startsWith("wlan") }
        .inetAddresses.toList().first { it is Inet4Address }
        .hostAddress.also {
            Log.i(tag,"hostAdress: $it")
        }

    val ownWsUri = URI(
        "ws",
        null,
        ownInternetAddress,
        wsServerPort,
        "/ws",
        null,
        null
    ).also {
        Log.i(tag,"ownWsUri: $it")
    }

    val ownServiceEndpoint = URI(
        "http",
        null,
        ownInternetAddress,
        wsServerPort + 5,
        "/didcomm",
        null,
        null
    ).also {
        Log.i(tag,"ownServicepoint: $it")
    }

    val ownDid = URI.create(
        createPeerDID(
            serviceEndpoint = ownServiceEndpoint.toString()
        )
    ).also {
        Log.i(tag,"ownDid: $it")
    }

    val credentialHolder = P256CryptoCredentials(
        KeyPair(
            "c9afa9d845ba75166b5c215767b1d6934e50c3db36e89b127b8a622b120f6721".hexToByteArray(),
            P256CryptoCredentials.createPublicKey("c9afa9d845ba75166b5c215767b1d6934e50c3db36e89b127b8a622b120f6721".hexToByteArray())
        )
    ).also {
        Log.i(tag,"holderDid: ${it.didKey}")
    }

    val keyStoreAlias = "MobileWallet2KeyStoreAlias"

    val biometricCredentialHolder = BiometricCryptoCredentials(
        BiometricCryptoCredentials.getKeyPair(keyStoreAlias) ?: BiometricCryptoCredentials.createKeyPair(keyStoreAlias)
    )

    val label = "Mobile Wallet 2"
}

