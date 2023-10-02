package de.gematik.security.mobilewallet.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.gematik.security.credentialExchangeLib.connection.Invitation
import de.gematik.security.credentialExchangeLib.protocols.Credential

class MainViewModel : ViewModel() {
    val invitations = MutableLiveData<ArrayList<Invitation>>()
    val credentials = MutableLiveData<ArrayList<Pair<String,Credential>>>()

    init {
        invitations.value = arrayListOf()
        credentials.value = arrayListOf()
    }

    fun setInvitations(invitations: HashMap<String, Invitation>) {
        this.invitations.postValue(ArrayList(invitations.values))
    }

    fun addInvitation(invitation: Invitation) {
        invitations.postValue(invitations.value?.apply {
            if (count { it.id == invitation.id } == 0) add(
                invitation
            )
        })
    }

    fun removeInvitations(position: Int) {
        invitations.postValue(invitations.value?.apply { removeAt(position) })
    }

    fun removeInvitations(id: String) {
        invitations.postValue(invitations.value?.apply { removeIf { it.id == id } })
    }

    fun removeAllInvitations() {
        invitations.postValue(arrayListOf())
    }

    fun setCredentials(credentials: HashMap<String, Credential>) {
        this.credentials.postValue(ArrayList(credentials.map{Pair(it.key, it.value)}))
    }

    fun addCredential(credential: Pair<String, Credential>) {
        credentials.postValue(credentials.value?.apply {
                add(credential)
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
        credentials.postValue(arrayListOf())
    }

}