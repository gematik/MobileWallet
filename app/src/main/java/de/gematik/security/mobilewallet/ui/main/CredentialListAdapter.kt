package de.gematik.security.mobilewallet.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.gematik.security.credentialExchangeLib.credentialSubjects.Insurance
import de.gematik.security.credentialExchangeLib.credentialSubjects.InsuranceType
import de.gematik.security.credentialExchangeLib.credentialSubjects.VaccinationEvent
import de.gematik.security.credentialExchangeLib.json
import de.gematik.security.credentialExchangeLib.protocols.Credential
import de.gematik.security.mobilewallet.MainActivity
import de.gematik.security.mobilewallet.R
import de.gematik.security.mobilewallet.databinding.CredentialCardBinding
import de.gematik.security.mobilewallet.toSimpleString
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Created by rk on 05.10.2021.
 * gematik.de
 */
class CredentialListAdapter(private val activity: MainActivity) :
    ListAdapter<Pair<String, Credential>, RecyclerView.ViewHolder>(CredentialDiffCallback) {

    var clickedPosition: Int = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // Create a new view, which defines the UI of credential card
        return CredentialViewHolder(
            CredentialCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        // Update the view hold by the given viewHolder
        (viewHolder as CredentialViewHolder).bind(getItem(position))
    }

    //view holder for card view
    inner class CredentialViewHolder(private val binding: CredentialCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                if (adapterPosition < 0) return@setOnClickListener
                getItem(adapterPosition).let { entry ->
                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.container, CredentialDetailFragment.newInstance(entry.first))
                        .addToBackStack("invitation_confirm").commit()
                }
            }
            binding.root.setOnLongClickListener {
                clickedPosition = adapterPosition
                false
            }
            binding.root.isLongClickable = true

        }

        fun bind(entry: Pair<String, Credential> ) {
            binding.apply {
                credentialId.text = "${entry.first.substring(0..7)}..${entry.first.substring(24)}"
                when {
                    entry.second.type.contains("PermanentResidentCard") -> {
                        label.text = "Resident Card"
                        entry.second.credentialSubject?.let {
                            content.text = String.format(
                                "%s %s\n%s",
                                it.getOrDefault("givenName", ""),
                                it.getOrDefault("familyName", "noName"),
                                it.getOrDefault("birthDate", "")
                            )
                        }?:"credential without subject"
                        imageView.setImageDrawable(activity.getDrawable(R.drawable.ic_identitycard))
                    }

                    entry.second.type.contains("InsuranceCertificate") -> {
                        label.text = "Insurance Certificate"
                        json.decodeFromJsonElement<Insurance>(entry.second.credentialSubject!!).let {
                            content.text = String.format(
                                "%s - %s - %s\n%s",
                                it.insurant.insurantId,
                                it.coverage?.start?.toSimpleString(),
                                it.coverage?.insuranceType?.name,
                                it.coverage?.costCenter?.name
                            )
                            imageView.setImageDrawable(activity.getDrawable(R.drawable.ic_identitycard))
                        }
                    }

                    entry.second.type.contains("VaccinationCertificate") -> {
                        label.text = "Vaccination Certificate"
                        json.decodeFromJsonElement<VaccinationEvent>(entry.second.credentialSubject!!).let {
                            content.text = String.format(
                                "%s - %s - %s\n%s",
                                it.vaccine?.medicalProductName,
                                it.dateOfVaccination?.toSimpleString(),
                                it.order,
                                it.administeringCentre
                            )
                            imageView.setImageDrawable(activity.getDrawable(R.drawable.ic_vaccination))
                        }
                    }

                    entry.second.type.contains("BaseIdDemo") -> {
                        label.text = "BaseId Demo"
                        entry.second.credentialSubject?.let {
                            content.text = String.format(
                                "%s %s\n%s",
                                it.getOrDefault("givenName", ""),
                                it.getOrDefault("familyName", "noName"),
                                it.getOrDefault("birthdate", "")
                            )
                        }?:"credential without subject"
                        imageView.setImageDrawable(activity.getDrawable(R.drawable.ic_identitycard))
                    }

                    entry.second.type.contains("NextcloudCredential") -> {
                        label.text = "Nextcloud Demo"
                        entry.second.credentialSubject?.let {
                            content.text = String.format(
                                "%s %s\n%s",
                                it.getOrDefault("givenName", ""),
                                it.getOrDefault("familyName", "noName"),
                                it.getOrDefault("email", "")
                            )
                        }?:"credential without subject"
                        imageView.setImageDrawable(activity.getDrawable(R.drawable.ic_nccard))
                    }

                    else -> {
                        label.text = "Certificate"
                        content.text = "Unknown credential type"
                        imageView.setImageDrawable(activity.getDrawable(R.drawable.ic_nccard))
                    }
                }
            }
        }
    }

    object CredentialDiffCallback : DiffUtil.ItemCallback<Pair<String,Credential>>() {
        override fun areItemsTheSame(oldItem: Pair<String,Credential>, newItem: Pair<String,Credential>): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Pair<String,Credential>, newItem: Pair<String,Credential>): Boolean {
            return oldItem.first == newItem.first
        }
    }
}