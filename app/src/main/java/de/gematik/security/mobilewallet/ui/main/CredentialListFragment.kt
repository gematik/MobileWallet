package de.gematik.security.mobilewallet.ui.main

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import de.gematik.security.mobilewallet.MainActivity
import de.gematik.security.mobilewallet.R
import de.gematik.security.mobilewallet.databinding.CredentialListFragmentBinding
import kotlinx.coroutines.runBlocking

class CredentialListFragment : Fragment() {

    private lateinit var binding: CredentialListFragmentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CredentialListFragmentBinding.inflate(inflater, container, false)
        val viewModel by activityViewModels<MainViewModel>()
        val adapter = CredentialListAdapter(activity as MainActivity)
        binding.credentialList.adapter = adapter
        viewModel.credentials.observe(viewLifecycleOwner) {
            adapter.submitList(it.toMutableList())
        }
        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.credential_list_fragment, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.delete_all_credentials ->
                runBlocking {
                    (activity as MainActivity).controller.removeAllCredentials()
                }
        }
        return false
    }
}