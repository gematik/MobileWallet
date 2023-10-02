package de.gematik.security.mobilewallet.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import de.gematik.security.credentialExchangeLib.connection.Invitation
import de.gematik.security.credentialExchangeLib.protocols.Credential
import de.gematik.security.credentialExchangeLib.protocols.GoalCode
import de.gematik.security.mobilewallet.MainActivity
import de.gematik.security.mobilewallet.Settings
import de.gematik.security.mobilewallet.Settings.from
import de.gematik.security.mobilewallet.databinding.ShowInvitationDialogFragmentBinding
import de.gematik.security.mobilewallet.qrCode
import java.util.*


class ShowInvitationDialogFragment : DialogFragment() {

    private lateinit var binding: ShowInvitationDialogFragmentBinding

    private var credential: Credential? = null

    var invitation: Invitation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        arguments?.let {
            it.getString(ARG_CREDENTIAL_ID)?.let {
                credential = (activity as MainActivity).controller.getCredential(it)?.value
            }
        }
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val type = credential?.type?.first { it != "VerifiableCredential" }
        val label = Settings.label
        val goal = if (type != null) "Present $type" else "Present credentials"
        val goalCode = GoalCode.OFFER_PRESENTATION
        binding = ShowInvitationDialogFragmentBinding.inflate(inflater, container, false)
        binding.credential.text = goal
        invitation = createInvitation(UUID.randomUUID().toString(), label, goal, goalCode)
        binding.qrcode.setImageBitmap(invitation?.qrCode)
        binding.Decline.setOnClickListener {
            dismiss()
        }
        return binding.root
    }

    companion object {
        private val ARG_CREDENTIAL_ID = "CredentialId"

        @JvmStatic
        fun newInstance(credentialId: String?) =
            ShowInvitationDialogFragment().apply {
                arguments = Bundle().apply {
                    credentialId?.let {
                        putString(ARG_CREDENTIAL_ID, it)
                    }
                }
            }
    }

    private fun createInvitation(invitationId: String, label: String, goal: String, goalCode: GoalCode): Invitation {
        return Invitation(
            id = invitationId,
            label = label,
            goal = goal,
            goalCode = goalCode,
            from = from
        )
    }
}