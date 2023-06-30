package de.gematik.security.mobilewallet.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import de.gematik.security.credentialExchangeLib.protocols.Context
import de.gematik.security.credentialExchangeLib.protocols.CredentialExchangeHolderContext
import de.gematik.security.mobilewallet.MainActivity
import de.gematik.security.mobilewallet.databinding.CredentialOfferDialogFragmentBinding
import kotlinx.coroutines.runBlocking
import java.util.*

class CredentialOfferDialogFragment : DialogFragment() {

    private lateinit var binding: CredentialOfferDialogFragmentBinding

    private var text: String? = null
    private var context: CredentialExchangeHolderContext? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            text = it.getString(ARG_TEXT)
            context = Context.getContext(UUID.fromString(it.getString(ARG_CONTEXT))) as CredentialExchangeHolderContext
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
                context?.let {
                    (activity as MainActivity).controller.handleCredentialOfferAccepted(it)
                }
            }
            dismiss()
        }
        binding.Decline.setOnClickListener {
            dismiss()
        }
        return binding.root
    }

    companion object {
        private val ARG_TEXT = "Text"
        private val ARG_CONTEXT = "Context"
        @JvmStatic
        fun newInstance(text: String, context: UUID) =
            CredentialOfferDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TEXT, text)
                    putString(ARG_CONTEXT, context.toString())
                }
            }
    }
}