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
import de.gematik.security.credentialExchangeLib.connection.Invitation
import de.gematik.security.mobilewallet.MainActivity
import de.gematik.security.mobilewallet.R
import de.gematik.security.mobilewallet.databinding.InvitationCardBinding

/**
 * Created by rk on 05.10.2021.
 * gematik.de
 */
class InvitationListAdapter(private val activity: MainActivity) :
    ListAdapter<Invitation, RecyclerView.ViewHolder>(InvitationDiffCallback) {

    var clickedPosition: Int = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // Create a new view, which defines the UI of an invitation card
        return InvitationViewHolder(
            InvitationCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        // Update the view hold by the given viewHolder
        val invitation = getItem(position)
        (viewHolder as InvitationViewHolder).bind(invitation)
    }

    //view holder for card view
    inner class InvitationViewHolder(private val binding: InvitationCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                if (adapterPosition < 0) return@setOnClickListener
                getItem(adapterPosition).id?.let { id ->
                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.container, InvitationDetailFragment.newInstance(id)).addToBackStack( "invitation_confirm" ).commit()
                }
            }
            binding.root.setOnLongClickListener {
                clickedPosition = adapterPosition
                false
            }
            binding.root.isLongClickable = true
        }

        fun bind(invitation: Invitation) {
            binding.apply {
                label.text = invitation.label
                invitationId.text = "${invitation.id?.substring(0..7)}..${invitation.id?.substring(24)}"
                goal.text = invitation.goal
            }
        }
    }
}

object InvitationDiffCallback : DiffUtil.ItemCallback<Invitation>() {
    override fun areItemsTheSame(oldItem: Invitation, newItem: Invitation): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: Invitation, newItem: Invitation): Boolean {
        return oldItem.id == newItem.id
    }
}
