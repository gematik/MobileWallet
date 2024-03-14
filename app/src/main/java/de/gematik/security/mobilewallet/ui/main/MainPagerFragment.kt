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
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayoutMediator
import com.journeyapps.barcodescanner.ScanOptions
import de.gematik.security.mobilewallet.MainActivity
import de.gematik.security.mobilewallet.R
import de.gematik.security.mobilewallet.databinding.MainPagerFragmentBinding

/**
 * Created by rk on 08.10.2021.
 * gematik.de
 */
class MainPagerFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = MainPagerFragmentBinding.inflate(inflater, container, false)
        val tabLayout = binding.tabs
        val viewPager = binding.viewPager
        viewPager.adapter = MainPagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position){
                INVITATIONS_PAGE_ID -> {
                    tab.setIcon(R.drawable.invitation_tab_selector)
                    tab.text = "Invitations"
                }
                CREDENTIALS_PAGE_ID ->{
                    tab.setIcon(R.drawable.credential_tab_selector)
                    tab.text = "Credentials"
                }
            }
        }.attach()

        binding.fab.setOnClickListener { view ->
            kotlin.runCatching {
                (activity as MainActivity).qrCodeScannerLauncher.launch(ScanOptions().apply {
                    this.setOrientationLocked(false)
                })
            }
        }

        return binding.root
    }
}

















