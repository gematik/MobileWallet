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
import android.text.method.ScrollingMovementMethod
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import de.gematik.security.credentialExchangeLib.json
import de.gematik.security.credentialExchangeLib.protocols.Credential
import de.gematik.security.mobilewallet.MainActivity
import de.gematik.security.mobilewallet.R
import de.gematik.security.mobilewallet.databinding.CredentialDetailFragmentBinding
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString



class CredentialDetailFragment : Fragment() {

    private val ARG_CREDENTIAL_ID = "CredentialId"

    private lateinit var binding: CredentialDetailFragmentBinding

    private var credentialId: String? = null
    private var credential: Credential? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            credentialId = it.getString(ARG_CREDENTIAL_ID)
        }
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CredentialDetailFragmentBinding.inflate(inflater, container, false)
        val viewModel by activityViewModels<MainViewModel>()
        credential = viewModel.credentials.value?.find { it.first == credentialId }?.second
        binding.textView.apply {
            text = json.encodeToString(credential)
            setHorizontallyScrolling(true)
            movementMethod = ScrollingMovementMethod()
        }
        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.credential_detail_fragment, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.delete_credential ->
                runBlocking {
                    (activity as MainActivity).run {
                        credentialId?.let{controller.removeCredential(it)}
                        supportFragmentManager.popBackStack()
                    }
                }
        }
        return false
    }

    companion object {
        @JvmStatic
        fun newInstance(id: String) =
            CredentialDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CREDENTIAL_ID, id)
                }
            }
    }
}