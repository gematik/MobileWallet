package de.gematik.security.mobilewallet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.gematik.security.credentialExchangeLib.json
import de.gematik.security.credentialExchangeLib.protocols.Invitation
import de.gematik.security.mobilewallet.ui.main.AboutDialogFragment
import de.gematik.security.mobilewallet.ui.main.MainPagerFragment
import de.gematik.security.mobilewallet.ui.main.ShowInvitationDialogFragment
import java.net.URI
import java.util.*

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

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

        // scan qr code
        qrCodeScannerLauncher = registerForActivityResult(ScanContract())
        {
            if (it.contents != null) {
                val oob = URI.create(it.contents).query.substringAfter("oob=", "").substringBefore("&")
                if (oob.isNotEmpty()) {
                    val invitation = json.decodeFromString<Invitation>(String(Base64.getDecoder().decode(oob)))
                    controller.acceptInvitation(invitation)
                }
            }
        }

        // start controller
        if (!this::controller.isInitialized) {
            controller = Controller(this@MainActivity).apply { start() }
        }

        // deep link clicked
        handleIntent(intent)

    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            val oob = (intent.data as Uri).getQueryParameter("oob")
            controller.acceptInvitation(json.decodeFromString<Invitation>(String(Base64.getDecoder().decode(oob))))
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
            R.id.sync -> {
                true
            }
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