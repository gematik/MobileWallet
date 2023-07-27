package de.gematik.security.mobilewallet.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.gematik.security.credentialExchangeLib.protocols.Credential
import de.gematik.security.credentialExchangeLib.protocols.Invitation
import java.util.*

class MainViewModel : ViewModel() {
    val invitations = MutableLiveData<ArrayList<Invitation>>()
    val credentials = MutableLiveData<ArrayList<Pair<String,Credential>>>()

    init {
        invitations.value = arrayListOf()
        credentials.value = arrayListOf()
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
        invitations.value = arrayListOf()
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
        credentials.value = arrayListOf()
    }

}