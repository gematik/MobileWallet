package de.gematik.security.mobilewallet.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import de.gematik.security.credentialExchangeLib.crypto.KeyPair
import de.gematik.security.credentialExchangeLib.crypto.ecdsa.EcdsaCryptoCredentials
import io.github.novacrypto.base58.Base58
import java.net.URI
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec

class BiometricCryptoCredentials(keyPair: KeyPair) : EcdsaCryptoCredentials(keyPair) {

    companion object {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
        val multiCodecId = byteArrayOf(0x80.toByte(), 0x24) // varint of 0x1200
        fun createKeyPair(keystoreAlias: String): KeyPair {
            val javaSecurityKeyPair =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                    initialize(
                        KeyGenParameterSpec.Builder(keystoreAlias, KeyProperties.PURPOSE_SIGN).apply {
                            setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                            setDigests(
                                KeyProperties.DIGEST_NONE
                            )
                            setUserAuthenticationRequired(true)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                            } else {
                                setUserAuthenticationValidityDurationSeconds(-1)
                            }
                            setAttestationChallenge("empty".toByteArray())
                            setIsStrongBoxBacked(true)
                        }.build()
                    )
                }.genKeyPair()

            return KeyPair(keystoreAlias.toByteArray(), javaSecurityKeyPair.public.encoded.let {
                convertPrimaryCodedPublicKeyToCompressedPublicKey(it)
            })
        }

        fun getKeyPair(keystoreAlias: String): KeyPair? {
            return (keyStore.getCertificateChain(keystoreAlias)?.firstOrNull() as? X509Certificate)?.publicKey?.encoded?.let {
                KeyPair(
                    keystoreAlias.toByteArray(),
                    convertPrimaryCodedPublicKeyToCompressedPublicKey(it)
                )
            }
        }

        private fun convertPrimaryCodedPublicKeyToCompressedPublicKey(primaryCodedPublicKey: ByteArray): ByteArray {
            return ByteArray(33).apply {
                this[0] = if (primaryCodedPublicKey[primaryCodedPublicKey.size - 1] % 2 == 0) 2.toByte() else 3.toByte()
                primaryCodedPublicKey.copyInto(
                    this,
                    1,
                    primaryCodedPublicKey.size - 64,
                    primaryCodedPublicKey.size - 32
                )
            }
        }

    }

    override val didKey: URI
    override val verificationMethod: URI
    val certificateChain = keyPair.publicKey?.let { keyStore.getCertificateChain(String(it)) }

    init {
        require(keyPair.privateKey != null)
        didKey = URI.create("did:key:z${Base58.base58Encode(multiCodecId + keyPair.publicKey!!)}")
        verificationMethod = URI.create("${didKey}#${didKey.toString().drop(8)}")
    }
}
