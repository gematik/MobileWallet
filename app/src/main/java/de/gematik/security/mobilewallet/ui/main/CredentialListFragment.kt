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

import android.os.Bundle
import android.view.*
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import de.gematik.security.mobilewallet.MainActivity
import de.gematik.security.mobilewallet.R
import de.gematik.security.mobilewallet.databinding.CredentialListFragmentBinding
import kotlinx.coroutines.runBlocking

class CredentialListFragment : Fragment() {

    private lateinit var binding: CredentialListFragmentBinding

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
        registerForContextMenu(binding.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.credential_list_fragment, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.delete_all_credentials ->
                        runBlocking {
                            (activity as MainActivity).controller.removeAllCredentials()
                            true
                        }

                    else -> false
                }
            }
        }, viewLifecycleOwner)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater: MenuInflater = requireActivity().menuInflater
        inflater.inflate(R.menu.credential_list_context_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.offer_credential -> {
                val viewModel by activityViewModels<MainViewModel>()
                val pos = (binding.credentialList.adapter as CredentialListAdapter).clickedPosition
                viewModel.credentials.value?.get(pos)?.let {
                    ShowInvitationDialogFragment.newInstance(
                        it.first
                    ).show(requireActivity().supportFragmentManager, "show_invitation")
                }
                true
            }

            R.id.delete_credential -> {
                val viewModel by activityViewModels<MainViewModel>()
                val pos = (binding.credentialList.adapter as CredentialListAdapter)
                    .clickedPosition
                val recordId = viewModel.credentials.value?.get(pos)?.first
                recordId?.let {
                    (activity as MainActivity).controller.removeCredential(it)
                }
                true
            }

            else -> super.onContextItemSelected(item)
        }
    }

}