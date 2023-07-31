package de.gematik.security.mobilewallet

import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.apicatalog.jsonld.JsonLd
import de.gematik.security.credentialExchangeLib.connection.WsConnection
import de.gematik.security.credentialExchangeLib.crypto.BbsPlusSigner
import de.gematik.security.credentialExchangeLib.crypto.ProofType
import de.gematik.security.credentialExchangeLib.defaultJsonLdOptions
import de.gematik.security.credentialExchangeLib.extensions.deepCopy
import de.gematik.security.credentialExchangeLib.extensions.toJsonDocument
import de.gematik.security.credentialExchangeLib.json
import de.gematik.security.credentialExchangeLib.protocols.*
import de.gematik.security.mobilewallet.ui.main.CREDENTIALS_PAGE_ID
import de.gematik.security.mobilewallet.ui.main.CredentialOfferDialogFragment
import de.gematik.security.mobilewallet.ui.main.MainViewModel
import de.gematik.security.mobilewallet.ui.main.PresentationSubmitDialogFragment
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private inner class InvitationCache() {
        private val invitations = HashMap<String, Invitation>()

        init {
            val preferences =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(mainActivity.applicationContext)
            val invitations = preferences.getStringSet("invitations", null)
            invitations?.forEach {
                addInvitation(Json.decodeFromString<Invitation>(it))
            }
        }

        val viewModel by mainActivity.viewModels<MainViewModel>()

        fun addInvitation(invitation: Invitation) {
            invitations.put(invitation.id, invitation)
            viewModel.addInvitation(invitation)
        }

        fun getInvitation(id: String): Invitation? {
            return invitations.get(id)
        }

        fun removeInvitation(id: String) {
            invitations.remove(id)
            viewModel.removeInvitations(id)
        }

        fun removeAllInvitations() {
            invitations.clear()
            viewModel.removeAllInvitations()
        }
    }

    private val invitationCache = InvitationCache()

    private inner class CredentialCache() {

        private val credentials = HashMap<String, Credential>()

        init {
            val preferences =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(mainActivity.applicationContext)
            val credentials = preferences.getStringSet("credentials", null)
            credentials?.forEach {
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
            while (it.protocolState.state != PresentationExchangeHolderProtocol.State.CLOSED) {
                debugState.presentationExchange = it.protocolState
                val message = runCatching {
                    it.receive()
                }.onFailure { Log.d(TAG, "exception: ${it.message}") }.getOrNull() ?: break
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
            invitationCache.addInvitation(invitation)
            invitation.service[0].serviceEndpoint?.let { serviceEndpoint ->
                Log.d(TAG, "invitation accepted from ${serviceEndpoint.host}:${serviceEndpoint.port}")
                when (invitation.goalCode) {
                    GoalCode.REQUEST_PRESENTATION -> PresentationExchangeHolderProtocol.connect(
                        WsConnection,
                        host = serviceEndpoint.host,
                        serviceEndpoint.port
                    ) {
                        it.sendInvitation(invitation)
                        while (it.protocolState.state != PresentationExchangeHolderProtocol.State.CLOSED) {
                            debugState.presentationExchange = it.protocolState
                            val message = runCatching {
                                it.receive()
                            }.onFailure { Log.d(TAG, "exception: ${it.message}") }.getOrNull() ?: break
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
                        while (it.protocolState.state != CredentialExchangeHolderProtocol.State.CLOSED) {
                            debugState.issueCredential = it.protocolState
                            val message = runCatching {
                                it.receive()
                            }.onFailure { Log.d(TAG, "exception: ${it.message}") }.getOrNull() ?: break
                            Log.d(TAG, "received: ${message.type}")
                            if (!handleIncomingMessage(it, message)) break
                        }
                        debugState.issueCredential = it.protocolState.copy()
                    }
                }
            }
        }
    }

    fun removeAllInvitations() {
        invitationCache.removeAllInvitations()
    }

    fun removeInvitation(id: String) {
        invitationCache.removeInvitation(id)
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
                protocolInstance.id,
                offer.outputDescriptor.frame.type.first { it != "VerifiableCredential" }
            ).show(mainActivity.supportFragmentManager, "credential_offer")
        }
        return true
    }

    suspend fun handleCredentialOfferAccepted(
        protocolInstance: CredentialExchangeHolderProtocol
    ) {
        val request = CredentialRequest(
            UUID.randomUUID().toString(),
            outputDescriptor = protocolInstance.protocolState.offer!!.outputDescriptor,
            holderKey = Settings.credentialHolder.didKey.toString()
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
        return if(protocolInstance.protocolState.invitation?.label != Settings.label){
            withContext(Dispatchers.Main) {
                PresentationSubmitDialogFragment.newInstance(
                    protocolInstance.id,
                    protocolInstance.protocolState.invitation?.goal ?: "unknown goal",
                    protocolInstance.protocolState.invitation?.label ?: "unknown verifier"
                ).show(mainActivity.supportFragmentManager, "presentation_sent")
            }
            true
        }else{
            handlePresentationRequestAccepted(protocolInstance)
            false
        }
    }

    suspend fun handlePresentationRequestAccepted(
        protocolInstance: PresentationExchangeHolderProtocol,
    ) {
        protocolInstance.protocolState.request?.let {
            val credentials = credentialCache.filterCredentials(it.inputDescriptor.frame)
            if (credentials.isEmpty()) return
            // pick credential - we pick the first credential without user interaction
            val derivedCredential =
                credentialCache.getCredential(credentials.get(0))?.value?.derive(it.inputDescriptor.frame)
            derivedCredential ?: return
            val ldProofHolder = LdProof(
                type = listOf(ProofType.BbsBlsSignature2020.name),
                created = Date(),
                creator = Settings.credentialHolder.didKey,
                proofPurpose = ProofPurpose.AUTHENTICATION,
                verificationMethod = Settings.credentialHolder.verificationMethod
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
                                    id = it.inputDescriptor.id,
                                    format = ClaimFormat.LDP_VC,
                                    path = "\$.verifiableCredential[0]"
                                )
                            )

                        )

                    ).apply {
                        sign(ldProofHolder, BbsPlusSigner(Settings.credentialHolder.keyPair))
                    }
                )
            )

        }
        protocolInstance.close()
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

