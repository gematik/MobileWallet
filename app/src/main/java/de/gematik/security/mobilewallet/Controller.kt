package de.gematik.security.mobilewallet

import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.apicatalog.jsonld.JsonLd
import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.document.RdfDocument
import de.gematik.security.credentialExchangeLib.connection.WsConnection
import de.gematik.security.credentialExchangeLib.crypto.BbsCryptoCredentials
import de.gematik.security.credentialExchangeLib.crypto.BbsPlusSigner
import de.gematik.security.credentialExchangeLib.crypto.KeyPair
import de.gematik.security.credentialExchangeLib.crypto.ProofType
import de.gematik.security.credentialExchangeLib.defaultJsonLdOptions
import de.gematik.security.credentialExchangeLib.extensions.deepCopy
import de.gematik.security.credentialExchangeLib.extensions.hexToByteArray
import de.gematik.security.credentialExchangeLib.extensions.normalize
import de.gematik.security.credentialExchangeLib.extensions.toJsonDocument
import de.gematik.security.credentialExchangeLib.protocols.*
import de.gematik.security.mobilewallet.ui.main.CREDENTIALS_PAGE_ID
import de.gematik.security.mobilewallet.ui.main.CredentialOfferDialogFragment
import de.gematik.security.mobilewallet.ui.main.MainViewModel
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.json.JSONArray
import java.net.URI
import java.util.*


/**
 * Created by rk on 04.08.2021.
 * gematik.de
 */

class Controller(val mainActivity: MainActivity) {
    val TAG = Controller::class.java.name

    private lateinit var job: Job

    val credentialHolder = BbsCryptoCredentials(
        KeyPair(
            "4318a7863ecbf9b347f3bd892828c588c20e61e5fa7344b7268643adb5a2bd4e".hexToByteArray(),
            "a21e0d512342b0b6ebf0d86ab3a2cef2a57bab0c0eeff0ffebad724107c9f33d69368531b41b1caa5728730f52aea54817b087f0d773cb1a753f1ede255468e88cea6665c6ce1591c88b079b0c4f77d0967d8211b1bc8687213e2af041ba73c4".hexToByteArray()
        )
    )

    private inner class InvitationCache() {
        private val invitations = HashMap<String, Invitation>()

        init {
            val preferences =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(mainActivity.applicationContext)
            val connectionRecords = preferences.getStringSet("connectionsRecords", null)
            connectionRecords?.forEach {
                addConnection(Json.decodeFromString<Invitation>(it))
            }
        }

        val viewModel by mainActivity.viewModels<MainViewModel>()

        fun addConnection(invitation: Invitation) {
            invitations.put(invitation.id, invitation)
            viewModel.addConnection(invitation)
        }

        fun getConnection(id: String): Invitation? {
            return invitations.get(id)
        }

        fun removeConnection(id: String) {
            invitations.remove(id)
            viewModel.removeConnection(id)
        }

        fun removeAllConnections() {
            invitations.clear()
            viewModel.removeAllConnections()
        }
    }

    private val invitationCache = InvitationCache()

    private inner class CredentialCache() {

        private val credentials = HashMap<String, Credential>()

        init {
            val preferences =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(mainActivity.applicationContext)
            val credentialRecords = preferences.getStringSet("credentialRecords", null)
            credentialRecords?.forEach {
                addCredential(Json.decodeFromString<Credential>(it))
            }
        }

        val viewModel by mainActivity.viewModels<MainViewModel>()

        fun addCredential(credential: Credential) {
            credentials.put(credential.id ?: UUID.randomUUID().toString(), credential)
            viewModel.addCredential(credential)
        }

        fun getCredential(id: String): Map.Entry<String, Credential> {
            return credentials.firstNotNullOf { it }
        }

        fun removeCredential(id: String) {
            credentials.remove(id)
            viewModel.removeCredential(id)
        }

        fun removeAllCredentials() {
            credentials.clear()
            viewModel.removeAllCredentials()
        }

        fun getFramedCredentials(frame: Credential): List<Credential> {
            //TODO: Implement framing
            return credentials.mapNotNull {
                val credentialWithoutProof = it.value.deepCopy().apply { proof = null }
                val transformedRdf =
                    credentialWithoutProof.normalize().trim().replace(Regex("_:c14n[0-9]*"), "<urn:bnid:$0>")
                val inputDocument =
                    JsonDocument.of(JsonLd.fromRdf(RdfDocument.of(transformedRdf.byteInputStream())).get())
                val frameDocument = frame.toJsonDocument()
                val jsonObject = JsonLd.frame(inputDocument, frameDocument).options(defaultJsonLdOptions).get()
                Json.decodeFromString<Credential>(jsonObject.toString())
            }
        }

    }

    private val credentialCache = CredentialCache()

    enum class SignatureType {
        Ed25519Signature2018,
        BbsBlsSignature2020
    }

    fun start() {
        job = mainActivity.lifecycleScope.launch {
            PresentationExchangeHolderProtocol.listen(WsConnection, port = Settings.wsServerPort) {
                while (true) {
                    val message = it.receive()
                    Log.d(TAG, "received: ${message.type}")
                    if (!handleIncomingMessage(it, message)) break
                }
            }
        }
    }

    fun restart() {
        runBlocking {
            kotlin.runCatching {
                job.cancelAndJoin()
            }
        }
        start()
    }

    fun acceptInvitation(invitation: Invitation) {
        mainActivity.lifecycleScope.launch(CoroutineName("")) {
            invitationCache.addConnection(invitation)
            invitation.service[0].serviceEndpoint?.let { serviceEndpoint ->
                Log.d(TAG, "invitation accepted from ${serviceEndpoint.host}")
                CredentialExchangeHolderProtocol.connect(
                    WsConnection,
                    host = serviceEndpoint.host,
                    serviceEndpoint.port
                ) {
                    it.sendInvitation(invitation)
                    while (true) {
                        val message = it.receive()
                        Log.d(TAG, "received: ${message.type}")
                        if (!handleIncomingMessage(it, message)) break
                    }
                }
            }
        }
    }

    fun removeAllConnections() {
        invitationCache.removeAllConnections()
    }

    fun removeConnection(id: String) {
        invitationCache.removeConnection(id)
    }

    fun removeAllCredentials() {
        credentialCache.removeAllCredentials()
    }

    fun removeCredential(id: String) {
        credentialCache.removeCredential(id)
    }

    fun getCredential(id: String): Map.Entry<String, Credential> {
        return credentialCache.getCredential(id)
    }

    // issue credentials

    private suspend fun handleIncomingMessage(
        protocolInstance: CredentialExchangeHolderProtocol,
        message: LdObject
    ): Boolean {
        val type = message.type ?: return true //ignore
        return when {
            type.contains("Close") -> false // close connection
            type.contains("CredentialOffer") -> handleCredentialOffer(protocolInstance, message as CredentialOffer)
            type.contains("CredentialSubmit") -> handleCredentialSubmit(protocolInstance, message as CredentialSubmit)
            else -> true //ignore
        }
    }

    private suspend fun handleCredentialOffer(
        protocolInstance: CredentialExchangeHolderProtocol,
        offer: CredentialOffer
    ): Boolean {
        withContext(Dispatchers.Main) {
            CredentialOfferDialogFragment.newInstance(
                offer.outputDescriptor.frame.type.first { it != "VerifiableCredential" },
                protocolInstance.id
            )
                .show(mainActivity.supportFragmentManager, "credential_offer")
        }
        return true
    }

    suspend fun handleCredentialOfferAccepted(
        protocolInstance: CredentialExchangeHolderProtocol
    ) {
        val request = CredentialRequest(
            UUID.randomUUID().toString(),
            outputDescriptor = protocolInstance.protocolState.offer!!.outputDescriptor,
            holderKey = credentialHolder.didKey.toString()
        )
        protocolInstance.requestCredential(request)
        Log.d(TAG, "sent: ${request.type}")
    }

    private suspend fun handleCredentialSubmit(
        protocolInstance: CredentialExchangeHolderProtocol,
        submit: CredentialSubmit
    ): Boolean {
        credentialCache.addCredential(submit.credential)
        Log.d(TAG, "stored: ${submit.credential.type}}")
        withContext(Dispatchers.Main) {
            mainActivity.findViewById<ViewPager2>(R.id.view_pager)?.currentItem = CREDENTIALS_PAGE_ID
        }
        return false
    }

    // presentation exchange

    private suspend fun handleIncomingMessage(
        protocolInstance: PresentationExchangeHolderProtocol,
        message: LdObject
    ): Boolean {
        val type = message.type ?: return true //ignore
        return when {
            type.contains("Close") -> false // close connection
            type.contains("Invitation") -> handleInvitation(protocolInstance, message as Invitation)
            type.contains("PresentationRequest") -> handlePresentationRequest(
                protocolInstance,
                message as PresentationRequest
            )

            else -> true //ignore
        }
    }

    private suspend fun handleInvitation(
        protocolInstance: PresentationExchangeHolderProtocol,
        message: Invitation
    ): Boolean {
        protocolInstance.sendOffer(
            PresentationOffer(
                UUID.randomUUID().toString(),
                inputDescriptor = Descriptor(
                    UUID.randomUUID().toString(), Credential(
                        atContext = Credential.DEFAULT_JSONLD_CONTEXTS + URI("https://w3id.org/vaccination/v1"),
                        type = Credential.DEFAULT_JSONLD_TYPES + "VaccinationCertificate"
                    )
                )
            )
        )
        return true
    }

    private suspend fun handlePresentationRequest(
        protocolInstance: PresentationExchangeHolderProtocol,
        message: PresentationRequest
    ): Boolean {
        val credentials = credentialCache.getFramedCredentials(message.inputDescriptor.frame)
        // pick credential - we pick the first credential without user interaction
        val derivedCredential = credentialCache.getCredential(credentials.get(0).id!!).value.derive(message.inputDescriptor.frame)

        val ldProofHolder = LdProof(
            type = listOf(ProofType.BbsBlsSignature2020.name),
            created = Date(),
            creator = credentialHolder.didKey,
            proofPurpose = ProofPurpose.AUTHENTICATION,
            verificationMethod = credentialHolder.verificationMethod
        )

        protocolInstance.submitPresentation(
            PresentationSubmit(
                UUID.randomUUID().toString(),
                presentation = Presentation(
                    id = UUID.randomUUID().toString(),
                    verifiableCredential = listOf(
                        derivedCredential
                    ),
                    presentationSubmission = PresentationSubmission(
                        definitionId = UUID.randomUUID(),
                        descriptorMap = listOf(
                            PresentationSubmission.DescriptorMapEntry(
                                id = message.inputDescriptor.id,
                                format = ClaimFormat.LDP_VC,
                                path = "\$.verifiableCredential[0]"
                            )
                        )

                    )

                ).apply {
                    sign(ldProofHolder, BbsPlusSigner(credentialHolder.keyPair))
                }
            )
        )
        return false
    }

}
