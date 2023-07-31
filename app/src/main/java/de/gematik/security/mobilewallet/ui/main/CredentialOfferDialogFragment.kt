package de.gematik.security.mobilewallet.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import de.gematik.security.credentialExchangeLib.protocols.CredentialExchangeHolderProtocol
import de.gematik.security.credentialExchangeLib.protocols.Protocol
import de.gematik.security.mobilewallet.MainActivity
import de.gematik.security.mobilewallet.databinding.CredentialOfferDialogFragmentBinding
import kotlinx.coroutines.runBlocking
import java.util.*

class CredentialOfferDialogFragment : DialogFragment() {

    private lateinit var binding: CredentialOfferDialogFragmentBinding

    private var text: String? = null
    private var protocolInstance: CredentialExchangeHolderProtocol? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            text = it.getString(ARG_TEXT)
            protocolInstance = Protocol.getProtocolInstance(UUID.fromString(it.getString(ARG_PROTOCOL_INSTANCE_ID))) as CredentialExchangeHolderProtocol
        }
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CredentialOfferDialogFragmentBinding.inflate(inflater, container, false)
        binding.credential.text = text
        binding.Accept.setOnClickListener {
            runBlocking {
                protocolInstance?.let {
                    (activity as MainActivity).controller.handleCredentialOfferAccepted(it)
                }
            }
            dismiss()
        }
        binding.Decline.setOnClickListener {
            protocolInstance?.close()
            dismiss()
        }
        return binding.root
    }

    companion object {
        private val ARG_TEXT = "Text"
        private val ARG_PROTOCOL_INSTANCE_ID = "ProtocolInstanceId"
        @JvmStatic
        fun newInstance(protocolInstanceId: UUID, text: String) =
            CredentialOfferDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROTOCOL_INSTANCE_ID, protocolInstanceId.toString())
                    putString(ARG_TEXT, text)
                }
            }
    }
}