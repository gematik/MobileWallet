package de.gematik.security.mobilewallet

import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import androidx.viewpager2.widget.ViewPager2
import com.apicatalog.jsonld.JsonLd
import de.gematik.security.credentialExchangeLib.connection.DidCommV2OverHttp.DIDDocResolverPeerDID
import de.gematik.security.credentialExchangeLib.connection.DidCommV2OverHttp.DidCommV2OverHttpConnection
import de.gematik.security.credentialExchangeLib.connection.Invitation
import de.gematik.security.credentialExchangeLib.connection.websocket.WsConnection
import de.gematik.security.credentialExchangeLib.crypto.ProofType
import de.gematik.security.credentialExchangeLib.defaultJsonLdOptions
import de.gematik.security.credentialExchangeLib.extensions.createUri
import de.gematik.security.credentialExchangeLib.extensions.deepCopy
import de.gematik.security.credentialExchangeLib.extensions.toJsonDocument
import de.gematik.security.credentialExchangeLib.json
import de.gematik.security.credentialExchangeLib.protocols.*
import de.gematik.security.mobilewallet.Settings.localEndpoint
import de.gematik.security.mobilewallet.Settings.ownDid
import de.gematik.security.mobilewallet.Settings.ownServiceEndpoint
import de.gematik.security.mobilewallet.ui.main.CREDENTIALS_PAGE_ID
import de.gematik.security.mobilewallet.ui.main.CredentialOfferDialogFragment
import de.gematik.security.mobilewallet.ui.main.MainViewModel
import de.gematik.security.mobilewallet.ui.main.ShowInvitationDialogFragment
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
import java.security.InvalidParameterException
import java.time.ZonedDateTime
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
            invitations.put(invitation.id!!, invitation)
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
            val frameContext = frame.atContext ?: return emptyList()
            return credentials.mapNotNull {
                // check if contexts match
                frameContext.forEach { uri ->
                    if (it.value.atContext?.contains(uri) == false) {
                        return@mapNotNull null
                    }
                }
                // frame credential and check if credential subject isn't null
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

    // protocol etstablishment
    // invitations are received by
    // a) scanning an QR-code,
    // b) clicking an QR-code or
    // c) tapping an NFC tag.
    // Further processing depends on goal code.
    fun acceptInvitation(invitation: Invitation) {
        mainActivity.lifecycleScope.launch(CoroutineName("")) {
            invitationStore.addInvitation(invitation)
            val (connectionFactory, to, from) = when (invitation.from.scheme) {
                "ws", "wss" -> {
                    Triple(
                        WsConnection,
                        invitation.from,
                        null
                    )
                }

                "did" -> {
                    Triple(
                        DidCommV2OverHttpConnection,
                        invitation.from,
                        ownDid
                    )
                }

                else -> {
                    throw InvalidParameterException("unsupported URI scheme: ${invitation.from.scheme}")
                }
            }
            Log.d(TAG, "invitation received from $to")
            when (invitation.goalCode) {
                GoalCode.REQUEST_PRESENTATION -> PresentationExchangeHolderProtocol.connect(
                    connectionFactory,
                    to = to,
                    from = from,
                    invitationId = invitation.id
                ) {
                    debugState.presentationExchangeInvitation = invitation
                    debugState.presentationExchange = it.protocolState
                    while (it.protocolState.state != PresentationExchangeHolderProtocol.State.CLOSED) {
                        val message = runCatching {
                            it.receive()
                        }.onFailure { Log.d(TAG, "exception: ${it.message}") }.getOrNull() ?: break
                        Log.d(TAG, "received: ${message.type}")
                        if (!handleIncomingMessage(it, message)) break
                    }
                }

                GoalCode.OFFER_PRESENTATION -> PresentationExchangeHolderProtocol.connect(
                    connectionFactory,
                    to = to,
                    from = from,
                    invitationId = invitation.id
                ) {
                    debugState.presentationExchangeInvitation = invitation
                    debugState.presentationExchange = it.protocolState
                    handleInvitation(it, invitation)
                    while (it.protocolState.state != PresentationExchangeHolderProtocol.State.CLOSED) {
                        val message = runCatching {
                            it.receive()
                        }.onFailure { Log.d(TAG, "exception: ${it.message}") }.getOrNull() ?: break
                        Log.d(TAG, "received: ${message.type}")
                        if (!handleIncomingMessage(it, message)) break
                    }
                }

                else -> CredentialExchangeHolderProtocol.connect(
                    connectionFactory,
                    to = to,
                    from = from,
                    invitationId = invitation.id
                ) {
                    debugState.issueCredentialInvitation = invitation
                    debugState.issueCredential = it.protocolState
                    while (it.protocolState.state != CredentialExchangeHolderProtocol.State.CLOSED) {
                        val message = runCatching {
                            it.receive()
                        }.onFailure { Log.d(TAG, "exception: ${it.message}") }.getOrNull() ?: break
                        Log.d(TAG, "received: ${message.type}")
                        if (!handleIncomingMessage(it, message)) break
                    }
                }
            }
        }
    }

    fun start() {
        // start didcomm listener
        PresentationExchangeHolderProtocol.listen(DidCommV2OverHttpConnection, ownServiceEndpoint) {
            listen(it)
        }

        // start WsSocket listener
        PresentationExchangeHolderProtocol.listen(WsConnection, localEndpoint) {
            listen(it)
        }

        // endpoint for debugging and demo purposes
        embeddedServer(CIO, port = Settings.wsServerPort + 1, host = "0.0.0.0", module = Application::module).start()
    }

    private suspend fun listen(protocol: PresentationExchangeHolderProtocol) {
        // invitation accept messages are only accepted when corresponding invitation is shown on screen.
        // invitation id of received invitation is hold in protocolState
        val activeFragment = mainActivity.supportFragmentManager.fragments.last()
        val invitation = (activeFragment as? ShowInvitationDialogFragment)?.invitation
        if (invitation?.id != null && invitation.id == protocol.protocolState.invitationId) {
            debugState.presentationExchangeInvitation = invitation
            debugState.presentationExchange = protocol.protocolState
            handleInvitation(protocol, invitation)
            while (protocol.protocolState.state != PresentationExchangeHolderProtocol.State.CLOSED) {
                val message = runCatching {
                    protocol.receive()
                }.onFailure { Log.d(TAG, "exception: ${it.message}") }.getOrNull() ?: break
                Log.d(TAG, "received: ${message.type}")
                if (!handleIncomingMessage(protocol, message)) break
            }
        }else{
            Log.d(TAG, "invitation ignored due to missing or wrong invitation id")
        }
    }


    // issue credentials
    private suspend fun handleIncomingMessage(
        protocolInstance: CredentialExchangeHolderProtocol,
        message: LdObject
    ): Boolean {
        val type = message.type
        return when {
            type.contains("Close") -> false // close connection
            type.contains("CredentialOffer") -> handleCredentialOffer(protocolInstance, message as CredentialOffer)
            type.contains("CredentialSubmit") -> handleCredentialSubmit(message as CredentialSubmit)
            else -> true //ignore
        }
    }

    // credential issueing start with an offer from the issuer
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

    // user needs to accept offer before sending request
    suspend fun handleCredentialOfferAccepted(
        protocolInstance: CredentialExchangeHolderProtocol
    ) {
        val request = CredentialRequest(
            UUID.randomUUID().toString(),
            outputDescriptor = protocolInstance.protocolState.offer!!.outputDescriptor,
            holderKey = Settings.biometricCredentialHolder.didKey
        )
        protocolInstance.requestCredential(request)
        Log.d(TAG, "sent: ${request.type}")
    }

    // holder receives credential in response to his request
    private suspend fun handleCredentialSubmit(
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
        val type = message.type
        return when {
            type.contains("Close") -> false // close connection
            type.contains("PresentationRequest") -> handlePresentationRequest(
                protocolInstance,
                message as PresentationRequest
            )

            else -> true //ignore
        }
    }

    // invitation acceptances are answered with a credential offer
    private suspend fun handleInvitation(
        protocolInstance: PresentationExchangeHolderProtocol,
        message: Invitation
    ): Boolean {
        val offer = PresentationOffer(
            UUID.randomUUID().toString(),
            inputDescriptor = when {
                message.goal?.contains("VaccinationCertificate") == true -> listOf( // vaccination + identity
                    Descriptor(
                        id = UUID.randomUUID().toString(),
                        frame = Credential(
                            atContext = Credential.DEFAULT_JSONLD_CONTEXTS + URI("https://w3id.org/vaccination/v1"),
                            type = Credential.DEFAULT_JSONLD_TYPES + "VaccinationCertificate"
                        )
                    ),
                    Descriptor(
                        id = UUID.randomUUID().toString(),
                        Credential(
                            atContext = Credential.DEFAULT_JSONLD_CONTEXTS + URI("https://gematik.de/vsd/v1"),
                            type = Credential.DEFAULT_JSONLD_TYPES + "InsuranceCertificate"
                        )

                    )
                )

                message.goal?.contains("InsuranceCertificate") == true -> listOf( // complete insurance info
                    Descriptor(
                        id = UUID.randomUUID().toString(),
                        Credential(
                            atContext = Credential.DEFAULT_JSONLD_CONTEXTS + URI("https://gematik.de/vsd/v1"),
                            type = Credential.DEFAULT_JSONLD_TYPES + "InsuranceCertificate"
                        )

                    )
                )

                else -> listOf()
            }

        )
        protocolInstance.sendOffer(offer)
        Log.d(TAG, "sent: ${offer.type}")
        return true
    }


    // presentation requests are accepted as response of an offer
    suspend fun handlePresentationRequest(
        protocolInstance: PresentationExchangeHolderProtocol,
        presentationRequest: PresentationRequest
    ): Boolean {
        presentationRequest.let {
            val presentation = Presentation(
                id = UUID.randomUUID().toString(),
                verifiableCredential = mutableListOf(),
                presentationSubmission = PresentationSubmission(
                    definitionId = UUID.randomUUID(),
                    descriptorMap = mutableListOf()
                )
            )
            it.inputDescriptor.forEachIndexed { index, descriptor ->
                val credentials = credentialStore.filterCredentials(descriptor.frame)
                if (credentials.isEmpty()) {
                    Toast.makeText(mainActivity, "No sufficient credential found!", Toast.LENGTH_LONG).show()
                    return false
                }
                // pick credential - we pick the first suitable credential without user interaction
                val derivedCredential =
                    credentialStore.getCredential(credentials.get(0))?.value?.derive(descriptor.frame)
                if (derivedCredential == null) {
                    Toast.makeText(mainActivity, "Could not derive credential!", Toast.LENGTH_LONG).show()
                    return false
                }
                (presentation.verifiableCredential as MutableList).add(index, derivedCredential)
                (presentation.presentationSubmission.descriptorMap as MutableList).add(
                    index,
                    PresentationSubmission.DescriptorMapEntry(
                        id = descriptor.id,
                        format = ClaimFormat.LDP_VC,
                        path = "\$.verifiableCredential[$index]"
                    )
                )
            }
            val ldProofHolder = LdProof(
                atContext = listOf(URI("https://www.w3.org/2018/credentials/v1")),
                type = listOf(ProofType.EcdsaSecp256r1Signature2019.name),
                created = ZonedDateTime.now(),
                creator = Settings.biometricCredentialHolder.didKey,
                proofPurpose = ProofPurpose.AUTHENTICATION,
                verificationMethod = Settings.biometricCredentialHolder.verificationMethod
            )

            val presentationSubmit = PresentationSubmit(
                UUID.randomUUID().toString(),
                presentation = presentation.apply {
                    asyncSign(
                        ldProofHolder,
                        Settings.biometricCredentialHolder.keyPair.privateKey!!,
                        mainActivity
                    )
                }
            )

            runCatching {
                protocolInstance.submitPresentation(presentationSubmit)
            }.onFailure { Toast.makeText(mainActivity, "$it", Toast.LENGTH_LONG).show() }.getOrThrow()
            Log.d(TAG, "sent: ${presentationSubmit.type}")
            mainActivity.supportFragmentManager.run {
                findFragmentByTag("show_invitation")?.let {
                    this.beginTransaction().remove(it).commit()
                }

            }
            Toast.makeText(
                mainActivity,
                "${presentation.verifiableCredential.size} credential${if(presentation.verifiableCredential.size >1) "s" else ""} sent",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
    }
}

// live debugging

@Serializable
data class DebugState(
    var issueCredentialInvitation: Invitation? = null,
    var issueCredential: CredentialExchangeHolderProtocol.ProtocolState? = null,
    var presentationExchangeInvitation: Invitation? = null,
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

