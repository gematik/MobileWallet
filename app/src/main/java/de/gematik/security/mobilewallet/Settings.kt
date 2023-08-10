package de.gematik.security.mobilewallet

import de.gematik.security.credentialExchangeLib.crypto.KeyPair
import de.gematik.security.credentialExchangeLib.crypto.ecdsa.P256CryptoCredentials
import de.gematik.security.credentialExchangeLib.extensions.hexToByteArray
import de.gematik.security.mobilewallet.crypto.BiometricCryptoCredentials
import java.util.*

object Settings {
    val wsServerPort = 9090
    val credentialHolder = P256CryptoCredentials(
        KeyPair(
            "c9afa9d845ba75166b5c215767b1d6934e50c3db36e89b127b8a622b120f6721".hexToByteArray(),
            P256CryptoCredentials.createPublicKey("c9afa9d845ba75166b5c215767b1d6934e50c3db36e89b127b8a622b120f6721".hexToByteArray())
        )
    )

    val biometricCredentialHolder = BiometricCryptoCredentials(
        BiometricCryptoCredentials.createKeyPair(UUID.randomUUID().toString())
    )

    val label = "Mobile Wallet 2"
}

