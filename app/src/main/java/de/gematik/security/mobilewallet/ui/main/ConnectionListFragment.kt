package de.gematik.security.mobilewallet.ui.main

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import de.gematik.security.mobilewallet.MainActivity
import de.gematik.security.mobilewallet.R
import de.gematik.security.mobilewallet.databinding.ConnectionListFragmentBinding

class ConnectionListFragment : Fragment() {

    private lateinit var binding: ConnectionListFragmentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ConnectionListFragmentBinding.inflate(inflater, container, false)
        val viewModel by activityViewModels<MainViewModel>()
        val adapter = ConnectionListAdapter(activity as MainActivity)
        binding.connectionList.adapter = adapter
        viewModel.connections.observe(viewLifecycleOwner) {
            adapter.submitList(it.toMutableList())
        }
        registerForContextMenu(binding.root)
        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.connection_list_fragment, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.delete_all_connections -> {
                (activity as MainActivity).controller.removeAllConnections()
            }
        }
        return false
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater: MenuInflater = requireActivity().menuInflater
        inflater.inflate(R.menu.connect_list_context_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.request_credential -> {
                val viewModel by activityViewModels<MainViewModel>()
                val pos = (binding.connectionList.adapter as ConnectionListAdapter).clickedPosition
                viewModel.connections.value?.get(pos)?.let {
                    (activity as MainActivity).controller.acceptInvitation(it)
                }
                true
            }

            R.id.delete_connection -> {
                val viewModel by activityViewModels<MainViewModel>()
                val pos = (binding.connectionList.adapter as ConnectionListAdapter)
                    .clickedPosition
                val recordId = viewModel.connections.value?.get(pos)?.id
                recordId?.let {
                    (activity as MainActivity).controller.removeConnection(it)
                }
                true
            }

            else -> super.onContextItemSelected(item)
        }
    }
}