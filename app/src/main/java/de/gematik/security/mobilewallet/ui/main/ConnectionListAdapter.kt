package de.gematik.security.mobilewallet.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.gematik.security.credentialExchangeLib.protocols.Invitation
import de.gematik.security.mobilewallet.MainActivity
import de.gematik.security.mobilewallet.R
import de.gematik.security.mobilewallet.databinding.ConnectionCardBinding

/**
 * Created by rk on 05.10.2021.
 * gematik.de
 */
class ConnectionListAdapter(private val activity: MainActivity) :
    ListAdapter<Invitation, RecyclerView.ViewHolder>(ConnectionDiffCallback) {

    var clickedPosition: Int = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // Create a new view, which defines the UI of connection card
        return ConnnectionViewHolder(
            ConnectionCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        // Update the view hold by the given viewHolder
        val connection = getItem(position)
        (viewHolder as ConnnectionViewHolder).bind(connection)
    }

    //view holder for card view
    inner class ConnnectionViewHolder(private val binding: ConnectionCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                if (adapterPosition < 0) return@setOnClickListener
                getItem(adapterPosition).id.let { id ->
                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.container, ConnectionDetailFragment.newInstance(id)).addToBackStack( "connection_confirm" ).commit()
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
                connectionId.text = "${invitation.id.substring(0..7)}..${invitation.id.substring(24)}"
                label.text = invitation.label
            }
        }
    }
}

object ConnectionDiffCallback : DiffUtil.ItemCallback<Invitation>() {
    override fun areItemsTheSame(oldItem: Invitation, newItem: Invitation): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: Invitation, newItem: Invitation): Boolean {
        return oldItem.id == newItem.id
    }
}
