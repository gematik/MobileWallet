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

package de.gematik.security.mobilewallet

import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.gematik.security.credentialExchangeLib.connection.Invitation
import de.gematik.security.credentialExchangeLib.crypto.CryptoRegistry
import de.gematik.security.credentialExchangeLib.crypto.ProofType
import de.gematik.security.credentialExchangeLib.json
import de.gematik.security.mobilewallet.crypto.BiometricSigner
import de.gematik.security.mobilewallet.t4tclient.T4TNdef
import de.gematik.security.mobilewallet.ui.main.AboutDialogFragment
import de.gematik.security.mobilewallet.ui.main.MainPagerFragment
import de.gematik.security.mobilewallet.ui.main.ShowInvitationDialogFragment
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.net.URI
import java.util.*


class MainActivity : AppCompatActivity() {

    private val tag = MainActivity::class.java.name

    lateinit var controller: Controller
    lateinit var qrCodeScannerLauncher: ActivityResultLauncher<ScanOptions>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, MainPagerFragment())
                .commitNow()
        }

        // register biometric signer
        CryptoRegistry.registerSigner(ProofType.EcdsaSecp256r1Signature2019) {
            BiometricSigner(it)
        }
        Log.i(tag, "biometric signer registered")

        // register qr code scanner
        qrCodeScannerLauncher = registerForActivityResult(ScanContract())
        {
            it.contents?.let { contents ->
                val oob = URI.create(contents).query?.substringAfter("oob=", "")?.substringBefore("&")
                if (oob?.isNotEmpty() == true) {
                    Log.i(tag, "Qr code scanned: $oob")
                    controller.acceptInvitation(
                        json.decodeFromString<Invitation>(String(Base64.getDecoder().decode(oob))).also{
                            Log.i(tag,"invitation = $it")
                        }
                    )
                }
            }
        }
        Log.i(tag, "qr code scanner registered")

        // start controller
        if (!this::controller.isInitialized) {
            controller = Controller(this@MainActivity).apply { start() }
            Log.i(tag, "controller registered")
        }else{
            Log.i(tag, "controller already registered")
        }

        // deep link clicked
        lifecycleScope.launch { handleIntent(intent) }
    }

    override fun onPause() {
        super.onPause()
        if (this::controller.isInitialized) {
            controller.saveStores()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        lifecycleScope.launch { handleIntent(intent) }
    }

    suspend private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> (intent.data as Uri).getQueryParameter("oob")
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                (intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.get(0) as NdefMessage)
                    .getRecords()[0].toUri()
                    .getQueryParameter("oob")
            }

            NfcAdapter.ACTION_TECH_DISCOVERED -> {
                intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)?.let {
                    lifecycleScope.async {
                        T4TNdef(it).getNdefMessage()
                    }.await()?.records?.get(0)?.toUri()?.getQueryParameter("oob")
                }
            }
            else -> null
        }?.let {
            val decodedOob = String(Base64.getDecoder().decode(it))
            Log.i(tag, "${if(intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED) "NDEF Tag" else "Type 4 Tag"} scanned: $it")
            controller.acceptInvitation(
                json.decodeFromString<Invitation>(decodedOob).also {
                    Log.i(tag, "invitation = $it")
                }
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
//            R.id.settings -> {
//                supportFragmentManager.beginTransaction()
//                    .replace(R.id.container, SettingsFragment()).addToBackStack("settings").commit()
//            true
//        }
//            R.id.sync -> {
//                true
//            }
            R.id.about -> {
                AboutDialogFragment().show(supportFragmentManager, "AboutDialog")
                true
            }

            R.id.invitation -> {
                ShowInvitationDialogFragment.newInstance(null)
                    .show(supportFragmentManager, "show_invitation")
                true
            }

            else -> false
        }
    }
}