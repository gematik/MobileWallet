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
                CONNECTIONS_PAGE_ID -> {
                    tab.setIcon(R.drawable.connection_tab_selector)
                    tab.text = "Connections"
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

















