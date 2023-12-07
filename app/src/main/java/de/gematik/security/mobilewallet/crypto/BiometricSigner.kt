package de.gematik.security.mobilewallet.crypto

import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import de.gematik.security.credentialExchangeLib.crypto.AsyncSigner
import de.gematik.security.credentialExchangeLib.crypto.KeyPair
import de.gematik.security.credentialExchangeLib.crypto.Signer
import de.gematik.security.credentialExchangeLib.extensions.toByteArray
import io.ktor.util.reflect.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DLSequence
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.SignatureException

class BiometricSigner(keyPair: KeyPair) : Signer, AsyncSigner {
    private val tag = BiometricSigner::class.java.name

    override val keyPair: KeyPair
    val cryptoObject: BiometricPrompt.CryptoObject

    init {
        val keystoreAliasBytes = keyPair.privateKey
        require(keystoreAliasBytes != null)
        val keystoreAlias = String(keystoreAliasBytes)
        cryptoObject = BiometricPrompt.CryptoObject(
            Signature.getInstance("NONEwithECDSA")
                .apply { initSign(BiometricCryptoCredentials.keyStore.getKey(keystoreAlias, null) as PrivateKey) })
        this.keyPair = keyPair
    }

    private suspend fun authenticate(context: FragmentActivity) {
        kotlin.runCatching {
            // sign using hardware backed key
            withContext(Dispatchers.Main) {
                val channel = Channel<Exception?>() // repeat indicator: repeat if true
                do {
                    BiometricPrompt(
                        context,
                        ContextCompat.getMainExecutor(context),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationError(
                                errorCode: Int,
                                errString: CharSequence
                            ) {
                                Log.i(tag, "authentication error: $errorCode - $errString")
                                super.onAuthenticationError(errorCode, errString)
                                if (errorCode == 10) {
                                    channel.trySend(AuthenticationError("$errorCode - $errString"))
                                } else {
                                    channel.trySend(AuthenticationCancelledException("$errorCode - $errString"))
                                }
                            }

                            override fun onAuthenticationSucceeded(
                                result: BiometricPrompt.AuthenticationResult
                            ) {
                                Log.i(tag, "authentication succeeded")
                                super.onAuthenticationSucceeded(result)
                                channel.trySend(null)
                            }

                            override fun onAuthenticationFailed() {
                                // wrong fingerprint - authentication continues
                                Log.i(tag, "authentication failed")
                                super.onAuthenticationFailed()
                            }

                        }).authenticate(
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Biometric Authentication")
                            .setSubtitle("Proof holdership of credential")
                            .setNegativeButtonText("Cancel")
                            .build(),
                        cryptoObject
                    )
                    val exception = channel.receive()
                } while (when (exception) {
                        null -> false
                        is AuthenticationError -> true
                        is AuthenticationCancelledException -> throw exception
                        else -> throw InternalError("invalid exception: $exception")
                    }
                )

            }
        }.onFailure {
            Log.e(tag, "authentication exception: ${it.message ?: "exception without message"}")
        }.getOrThrow()
    }

    override fun sign(content: List<ByteArray>): ByteArray {
        throw IllegalStateException("Biometric signer requires user authentication. Use authenticate and asyncSign.")
    }

    override suspend fun asyncSign(content: List<ByteArray>, context: Any): ByteArray {
        check(context.instanceOf(FragmentActivity::class))
        runCatching {
            authenticate(context as FragmentActivity)
        }.onFailure {
            Log.e(tag, "signing exception: ${it.message}")
        }.getOrThrow()
        val hash = MessageDigest.getInstance("SHA-256").apply {
            content.forEach { update(it) }
        }.digest()
        val signatureBytes = cryptoObject.signature?.let {
            it.update(hash)
            it.sign()
        } ?: throw SignatureException("CryptoObject without signature")
        val input = ASN1InputStream(signatureBytes)
        val intList =
            (input.readObject() as DLSequence).objects.toList().map { (it as ASN1Integer).value.toByteArray(32) }
        return intList[0] + intList[1]
    }
}

class AuthenticationCancelledException(message: String) : Exception(message)
class AuthenticationError(message: String) : Exception(message)
