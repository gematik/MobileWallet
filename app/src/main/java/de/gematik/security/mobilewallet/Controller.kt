package de.gematik.security.mobilewallet

import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.apicatalog.jsonld.JsonLd
import de.gematik.security.credentialExchangeLib.connection.WsConnection
import de.gematik.security.credentialExchangeLib.crypto.BbsCryptoCredentials
import de.gematik.security.credentialExchangeLib.crypto.BbsPlusSigner
import de.gematik.security.credentialExchangeLib.crypto.KeyPair
import de.gematik.security.credentialExchangeLib.crypto.ProofType
import de.gematik.security.credentialExchangeLib.defaultJsonLdOptions
import de.gematik.security.credentialExchangeLib.extensions.deepCopy
import de.gematik.security.credentialExchangeLib.extensions.hexToByteArray
import de.gematik.security.credentialExchangeLib.extensions.toJsonDocument
import de.gematik.security.credentialExchangeLib.json
import de.gematik.security.credentialExchangeLib.protocols.*
import de.gematik.security.mobilewallet.ui.main.CREDENTIALS_PAGE_ID
import de.gematik.security.mobilewallet.ui.main.CredentialOfferDialogFragment
import de.gematik.security.mobilewallet.ui.main.MainViewModel
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.*


/**
 * Created by rk on 04.08.2021.
 * gematik.de
 */

class Controller(val mainActivity: MainActivity) {
    val TAG = Controller::class.java.name

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
            val id = credential.id ?: UUID.randomUUID().toString()
            credentials.put(id, credential)
            viewModel.addCredential(Pair(id, credential))
        }

        fun getCredential(id: String): Map.Entry<String, Credential>? {
            return credentials.firstNotNullOfOrNull { if (it.key == id) it else null }
        }

        fun removeCredential(id: String) {
            credentials.remove(id)
            viewModel.removeCredential(id)
        }

        fun removeAllCredentials() {
            credentials.clear()
            viewModel.removeAllCredentials()
        }

        fun filterCredentials(frame: Credential): List<String> {
            //TODO: Implement framing
            return credentials.mapNotNull {
                val credential = it.value.deepCopy().apply { proof = null }
                val inputDocument = credential.toJsonDocument()
                val frameDocument = frame.toJsonDocument()
                val jsonObject = JsonLd.frame(inputDocument, frameDocument).options(defaultJsonLdOptions).get()
                val framedCredential = Json.decodeFromString<Credential>(jsonObject.toString())
                if (framedCredential.credentialSubject != null) it.key else null
            }
        }

    }

    private val credentialCache = CredentialCache()

    enum class SignatureType {
        Ed25519Signature2018,
        BbsBlsSignature2020
    }

    fun start() {
        PresentationExchangeHolderProtocol.listen(WsConnection, port = Settings.wsServerPort) {
            while (true) {
                debugState.presentationExchange = it.protocolState
                val message = it.receive()
                Log.d(TAG, "received: ${message.type}")
                if (!handleIncomingMessage(it, message)) break
            }
            debugState.presentationExchange = it.protocolState.copy()
        }
        // endpoint for debugging and demo purposes
        embeddedServer(CIO, port = Settings.wsServerPort + 1, host = "0.0.0.0", module = Application::module).start()
    }

    fun acceptInvitation(invitation: Invitation) {
        mainActivity.lifecycleScope.launch(CoroutineName("")) {
            invitationCache.addConnection(invitation)
            invitation.service[0].serviceEndpoint?.let { serviceEndpoint ->
                Log.d(TAG, "invitation accepted from ${serviceEndpoint.host}:${serviceEndpoint.port}")
                when(invitation.goalCode) {
                    GoalCode.REQUEST_PRESENTATION -> PresentationExchangeHolderProtocol.connect(
                        WsConnection,
                        host = serviceEndpoint.host,
                        serviceEndpoint.port
                    ) {
                        it.sendInvitation(invitation)
                        while (true) {
                            debugState.presentationExchange = it.protocolState
                            val message = it.receive()
                            Log.d(TAG, "received: ${message.type}")
                            if (!handleIncomingMessage(it, message)) break
                        }
                        debugState.presentationExchange = it.protocolState.copy()
                    }

                    else -> CredentialExchangeHolderProtocol.connect(
                        WsConnection,
                        host = serviceEndpoint.host,
                        serviceEndpoint.port
                    ) {
                        it.sendInvitation(invitation)
                        while (true) {
                            debugState.issueCredential = it.protocolState
                            val message = it.receive()
                            Log.d(TAG, "received: ${message.type}")
                            if (!handleIncomingMessage(it, message)) break
                        }
                        debugState.issueCredential = it.protocolState.copy()
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

    fun getCredential(id: String): Map.Entry<String, Credential>? {
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
                        type = when {
                            message.goal.contains("VaccinationCertificate") -> Credential.DEFAULT_JSONLD_TYPES + "VaccinationCertificate"
                            message.goal.contains("InsuranceCertificate") -> Credential.DEFAULT_JSONLD_TYPES + "InsuranceCertificate"
                            else -> Credential.DEFAULT_JSONLD_TYPES
                        }
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
        val credentials = credentialCache.filterCredentials(message.inputDescriptor.frame)
        if (credentials.isEmpty()) return false
        // pick credential - we pick the first credential without user interaction
        val derivedCredential =
            credentialCache.getCredential(credentials.get(0))?.value?.derive(message.inputDescriptor.frame)
        derivedCredential ?: return false
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

// live debugging

@Serializable
data class DebugState(
    var issueCredential: CredentialExchangeHolderProtocol.ProtocolState? = null,
    var presentationExchange: PresentationExchangeHolderProtocol.ProtocolState? = null
)

val debugState = DebugState()

fun Application.module() {
    routing {
        get("/") {
            call.respondText(json.encodeToString(debugState), ContentType.parse("application/json"), HttpStatusCode.OK)
        }
    }
}

