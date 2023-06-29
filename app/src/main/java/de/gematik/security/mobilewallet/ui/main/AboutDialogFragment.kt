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

