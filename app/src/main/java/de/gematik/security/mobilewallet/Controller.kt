package de.gematik.security.mobilewallet

import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import androidx.viewpager2.widget.ViewPager2
import com.apicatalog.jsonld.JsonLd
import de.gematik.security.credentialExchangeLib.connection.WsConnection
import de.gematik.security.credentialExchangeLib.crypto.ProofType
import de.gematik.security.credentialExchangeLib.defaultJsonLdOptions
import de.gematik.security.credentialExchangeLib.extensions.deepCopy
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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.util.*


/**
 * Created by rk on 04.08.2021.
 * gematik.de
 */

class Controller(val mainActivity: MainActivity) {
    val TAG = Controller::class.java.name

    val viewModel by mainActivity.viewModels<MainViewModel>()

    val masterKey = MasterKey.Builder(mainActivity)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setRequestStrongBoxBacked(true)
        .build()

    private inner class InvitationStore() {
        private val invitations: HashMap<String, Invitation>

        val fileStorageName: String = "invitations"

        init {
            val restoredInvitations = runCatching {
                val file = File(mainActivity.filesDir, fileStorageName)
                val encryptedFile: EncryptedFile = EncryptedFile.Builder(
                    mainActivity,
                    file,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                json.decodeFromString<HashMap<String, Invitation>>(encryptedFile.openFileInput().use {
                    it.bufferedReader().use {
                        it.readText()
                    }
                })
            }.getOrNull() ?: HashMap<String, Invitation>()
            invitations = restoredInvitations
            viewModel.setInvitations(invitations)
        }


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

        fun save() {
            mainActivity.lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    val file = File(mainActivity.filesDir, fileStorageName)
                    if (file.exists()) {
                        file.delete()
                    }
                    val encryptedFile: EncryptedFile = EncryptedFile.Builder(
                        mainActivity,
                        file,
                        masterKey,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    ).build()
                    encryptedFile.openFileOutput().use {
                        it.bufferedWriter().use {
                            it.write(json.encodeToString<HashMap<String, Invitation>>(invitations))
                        }
                    }
                }
            }
        }
    }

    private val invitationStore = InvitationStore()

    private inner class CredentialStore() {

        private val credentials: HashMap<String, Credential>

        val fileStorageName: String = "credentials"

        init {
            val restoredCredentials = runCatching {
                val file = File(mainActivity.filesDir, fileStorageName)
                val encryptedFile: EncryptedFile = EncryptedFile.Builder(
                    mainActivity,
                    file,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                json.decodeFromString<HashMap<String, Credential>>(encryptedFile.openFileInput().use {
                    it.bufferedReader().use {
                        it.readText()
                    }
                })
            }.getOrNull() ?: HashMap<String, Credential>()
            credentials = restoredCredentials
            viewModel.setCredentials(credentials)
        }

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

        fun save() {
            runCatching {
                val file = File(mainActivity.filesDir, fileStorageName)
                if (file.exists()) {
                    file.delete()
                }
                val encryptedFile: EncryptedFile = EncryptedFile.Builder(
                    mainActivity,
                    file,
                    masterKey,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                encryptedFile.openFileOutput().use {
                    it.bufferedWriter().use {
                        it.write(json.encodeToString<HashMap<String, Credential>>(credentials))
                    }
                }
            }
        }
    }

    private val credentialStore = CredentialStore()

    fun saveStores() {
        invitationStore.save()
        credentialStore.save()
    }

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
            invitationStore.addInvitation(invitation)
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
        invitationStore.removeAllInvitations()
    }

    fun removeInvitation(id: String) {
        invitationStore.removeInvitation(id)
    }

    fun removeAllCredentials() {
        credentialStore.removeAllCredentials()
    }

    fun removeCredential(id: String) {
        credentialStore.removeCredential(id)
    }

    fun getCredential(id: String): Map.Entry<String, Credential>? {
        return credentialStore.getCredential(id)
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
            holderKey = Settings.biometricCredentialHolder.didKey.toString()
        )
        protocolInstance.requestCredential(request)
        Log.d(TAG, "sent: ${request.type}")
    }

    private suspend fun handleCredentialSubmit(
        protocolInstance: CredentialExchangeHolderProtocol,
        submit: CredentialSubmit
    ): Boolean {
        credentialStore.addCredential(submit.credential)
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

    suspend fun handlePresentationRequest(
        protocolInstance: PresentationExchangeHolderProtocol,
        presentationRequest: PresentationRequest
    ): Boolean {
        presentationRequest.let {
            val credentials = credentialStore.filterCredentials(it.inputDescriptor.frame)
            if (credentials.isEmpty()) return false
            // pick credential - we pick the first credential without user interaction
            val derivedCredential =
                credentialStore.getCredential(credentials.get(0))?.value?.derive(it.inputDescriptor.frame)
            derivedCredential ?: return false
            val ldProofHolder = LdProof(
                atContext = listOf(URI("https://www.w3.org/2018/credentials/v1")),
                type = listOf(ProofType.EcdsaSecp256r1Signature2019.name),
                created = Date(),
                creator = Settings.biometricCredentialHolder.didKey,
                proofPurpose = ProofPurpose.AUTHENTICATION,
                verificationMethod = Settings.biometricCredentialHolder.verificationMethod
            )

            protocolInstance.submitPresentation(
                runCatching {
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
                            asyncSign(
                                ldProofHolder,
                                Settings.biometricCredentialHolder.keyPair.privateKey!!,
                                mainActivity
                            )
                        }
                    )
                }.onFailure { Toast.makeText(mainActivity, "$it", Toast.LENGTH_LONG).show() }.getOrThrow()
            )
            mainActivity.supportFragmentManager.run {
                findFragmentByTag("show_invitation")?.let {
                    this.beginTransaction().remove(it).commit()
                }

            }
            return false
        }
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
            call.respondText(
                json.encodeToString(debugState),
                ContentType.parse("application/json"),
                HttpStatusCode.OK
            )
        }
    }
}

