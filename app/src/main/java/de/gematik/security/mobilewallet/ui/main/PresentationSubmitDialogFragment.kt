package de.gematik.security.mobilewallet.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import de.gematik.security.credentialExchangeLib.protocols.PresentationExchangeHolderProtocol
import de.gematik.security.credentialExchangeLib.protocols.Protocol
import de.gematik.security.mobilewallet.MainActivity
import de.gematik.security.mobilewallet.databinding.PresentationSubmitDialogFragmentBinding
import kotlinx.coroutines.runBlocking
import java.util.*

class PresentationSubmitDialogFragment : DialogFragment() {

    private lateinit var binding: PresentationSubmitDialogFragmentBinding

    private var goal: String? = null
    private var verifier: String? = null
    private var protocolInstance: PresentationExchangeHolderProtocol? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            goal = it.getString(ARG_GOAL)
            verifier = it.getString(ARG_VERIFIER)
            protocolInstance = Protocol.getProtocolInstance(UUID.fromString(it.getString(ARG_PROTOCOL_INSTANCE_ID))) as PresentationExchangeHolderProtocol
        }
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = PresentationSubmitDialogFragmentBinding.inflate(inflater, container, false)
        binding.goal.text = goal
        binding.verifier.text = verifier
        binding.Accept.setOnClickListener {
            runBlocking {
                protocolInstance?.let {
                    (activity as MainActivity).controller.handlePresentationRequestAccepted(it)
                }
            }
            dismiss()
        }
        binding.Decline.setOnClickListener{
            protocolInstance?.close()
            dismiss()
        }
        return binding.root
    }

    companion object {
        private val ARG_VERIFIER = "Verifier"
        private val ARG_GOAL = "Goal"
        private val ARG_PROTOCOL_INSTANCE_ID = "ProtocolInstanceId"
        @JvmStatic
        fun newInstance(protocolInstanceId: UUID, goal: String, verifier: String) =
            PresentationSubmitDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROTOCOL_INSTANCE_ID, protocolInstanceId.toString())
                    putString(ARG_GOAL, goal)
                    putString(ARG_VERIFIER, verifier)
                }
            }
    }
}