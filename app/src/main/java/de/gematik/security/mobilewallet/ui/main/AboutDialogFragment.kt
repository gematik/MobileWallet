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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import de.gematik.security.mobilewallet.BuildConfig
import de.gematik.security.mobilewallet.R
import de.gematik.security.mobilewallet.databinding.AboutDialogFragmentBinding

/**
 * Created by rk on 15.06.2022.
 * gematik.de
 */
class AboutDialogFragment : DialogFragment() {
    private lateinit var binding: AboutDialogFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AboutDialogFragmentBinding.inflate(inflater, container, false)
        binding.appName.text = getString(R.string.app_name)
        binding.appId.text = BuildConfig.APPLICATION_ID
        binding.appVersion.text = BuildConfig.VERSION_NAME
        return binding.root
    }

    companion object {
        @JvmStatic
        fun newInstance(text: String, payLoad: Any) = AboutDialogFragment()
    }
}

