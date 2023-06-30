package de.gematik.security.mobilewallet

import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import de.gematik.security.credentialExchangeLib.connection.WsConnection
import de.gematik.security.credentialExchangeLib.crypto.BbsCryptoCredentials
import de.gematik.security.credentialExchangeLib.crypto.KeyPair
import de.gematik.security.credentialExchangeLib.extensions.hexToByteArray
import de.gematik.security.credentialExchangeLib.protocols.*
import de.gematik.security.mobilewallet.ui.main.CREDENTIALS_PAGE_ID
import de.gematik.security.mobilewallet.ui.main.CredentialOfferDialogFragment
import de.gematik.security.mobilewallet.ui.main.MainViewModel
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
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

        fun removeCredential(id: String) {
            credentials.remove(id)
            viewModel.removeCredential(id)
        }

        fun removeAllCredentials() {
            credentials.clear()
            viewModel.removeAllCredentials()
        }

    }

    private val credentialCache = CredentialCache()

    enum class SignatureType {
        Ed25519Signature2018,
        BbsBlsSignature2020
    }

    fun start() {
        job = mainActivity.lifecycleScope.launch {
            CredentialExchangeHolderContext.listen(WsConnection) {
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
        mainActivity.lifecycleScope.launch {
            invitationCache.addConnection(invitation)
            invitation.service[0].serviceEndpoint?.let { serviceEndpoint ->
                Log.d(TAG, "invitation accepted from ${serviceEndpoint.host}")
                CredentialExchangeHolderContext.connect(
                    WsConnection,
                    host = serviceEndpoint.host,
                    serviceEndpoint.port,
                    invitation = invitation
                ) {
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

    private suspend fun handleIncomingMessage(context: CredentialExchangeHolderContext, message: LdObject): Boolean {
        val type = message.type ?: return true //ignore
        return when {
            type.contains("Close") -> false // close connection
            type.contains("CredentialOffer") -> handleCredentialOffer(context, message as CredentialOffer)
            type.contains("CredentialSubmit") -> handleCredentialSubmit(context, message as CredentialSubmit)
            else -> true //ignore
        }
    }

    private suspend fun handleCredentialOffer(context: CredentialExchangeHolderContext, offer: CredentialOffer): Boolean {
        withContext(Dispatchers.Main) {
            CredentialOfferDialogFragment.newInstance(offer.outputDescriptor.type.first{it != "VerifiableCredential"}, context.id)
                .show(mainActivity.supportFragmentManager, "credential_offer")
        }
        return true
    }

    private suspend fun handleCredentialSubmit(context: CredentialExchangeHolderContext, submit: CredentialSubmit): Boolean {
        credentialCache.addCredential(submit.credential)
        Log.d(TAG, "stored: ${submit.credential.type}}")
        withContext(Dispatchers.Main) {
            mainActivity.findViewById<ViewPager2>(R.id.view_pager)?.currentItem = CREDENTIALS_PAGE_ID
        }
        return false
    }

    suspend fun handleCredentialOfferAccepted(context: CredentialExchangeHolderContext){
        val request = CredentialRequest(
            UUID.randomUUID().toString(),
            outputDescriptor = context.protocolState.offer!!.outputDescriptor,
            holderKey = credentialHolder.didKey.toString()
        )
        context.requestCredential(request)
        Log.d(TAG, "sent: ${request.type}")
    }

}
