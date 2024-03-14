/*
 * Copyright 2021-2024, gematik GmbH
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

package de.gematik.security.mobilewallet.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.gematik.security.credentialExchangeLib.credentialSubjects.Insurance
import de.gematik.security.credentialExchangeLib.credentialSubjects.VaccinationEvent
import de.gematik.security.credentialExchangeLib.extensions.toObject
import de.gematik.security.credentialExchangeLib.json
import de.gematik.security.credentialExchangeLib.protocols.Credential
import de.gematik.security.mobilewallet.MainActivity
import de.gematik.security.mobilewallet.R
import de.gematik.security.mobilewallet.databinding.CredentialCardBinding
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.ZonedDateTime
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

        fun bind(entry: Pair<String, Credential>) {
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
                        } ?: "credential without subject"
                        imageView.setImageDrawable(activity.getDrawable(R.drawable.ic_identitycard))
                    }

                    entry.second.type.contains("InsuranceCertificate") -> {
                        label.text = "Insurance Certificate"
                        entry.second.credentialSubject?.toObject<Insurance>()?.let {
                            content.text = String.format(
                                "%s - %s - %s\n%s",
                                it.insurant.insurantId,
                                it.coverage?.start?.let {
                                    ZonedDateTime.parse(it).format(DateTimeFormatter.ISO_LOCAL_DATE)
                                },
                                it.coverage?.insuranceType?.name,
                                it.coverage?.costCenter?.name
                            )
                            imageView.setImageDrawable(activity.getDrawable(R.drawable.ic_identitycard))
                        }
                    }

                    entry.second.type.contains("VaccinationCertificate") -> {
                        label.text = "Vaccination Certificate"
                        entry.second.credentialSubject?.toObject<VaccinationEvent>()?.let {
                            content.text = String.format(
                                "%s - %s - %s\n%s",
                                it.vaccine?.medicalProductName,
                                it.dateOfVaccination?.let {
                                    ZonedDateTime.parse(it).format(DateTimeFormatter.ISO_LOCAL_DATE)
                                },
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
                        } ?: "credential without subject"
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
                        } ?: "credential without subject"
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

    object CredentialDiffCallback : DiffUtil.ItemCallback<Pair<String, Credential>>() {
        override fun areItemsTheSame(oldItem: Pair<String, Credential>, newItem: Pair<String, Credential>): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Pair<String, Credential>, newItem: Pair<String, Credential>): Boolean {
            return oldItem.first == newItem.first
        }
    }
}