package de.gematik.security.mobilewallet.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.gematik.security.credentialExchangeLib.protocols.Credential
import de.gematik.security.credentialExchangeLib.protocols.Invitation
import java.util.*

class MainViewModel : ViewModel() {
    val connections = MutableLiveData<ArrayList<Invitation>>()
    val credentials = MutableLiveData<ArrayList<Pair<String,Credential>>>()

    init {
        connections.value = arrayListOf()
        credentials.value = arrayListOf()
    }

    fun addConnection(invitation: Invitation) {
        connections.postValue(connections.value?.apply {
            if (count { it.id == invitation.id } == 0) add(
                invitation
            )
        })
    }

    fun removeConnection(position: Int) {
        connections.postValue(connections.value?.apply { removeAt(position) })
    }

    fun removeConnection(id: String) {
        connections.postValue(connections.value?.apply { removeIf { it.id == id } })
    }

    fun removeAllConnections() {
        connections.value = arrayListOf()
    }

    fun addCredential(credential: Credential) {
        credentials.postValue(credentials.value?.apply {
            val id = credential.id
            if(id!=null) {
                if (count { it.first == id } == 0) add(Pair(id, credential))
            }else{
                add(Pair(UUID.randomUUID().toString(), credential))
            }
        })
    }

    fun removeCredential(position: Int) {
        credentials.postValue(credentials.value?.apply { removeAt(position) })
    }

    fun removeCredential(id: String) {
        credentials.postValue(credentials.value?.apply {
            removeIf { it.first == id }
        })
    }

    fun removeAllCredentials() {
        credentials.value = arrayListOf()
    }

}