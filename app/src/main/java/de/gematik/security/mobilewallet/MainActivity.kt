package de.gematik.security.mobilewallet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.gematik.security.credentialExchangeLib.json
import de.gematik.security.credentialExchangeLib.protocols.Invitation
import de.gematik.security.mobilewallet.ui.main.AboutDialogFragment
import de.gematik.security.mobilewallet.ui.main.MainPagerFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                val oob = URI.create(it.contents).query.substringAfter("oob=", "")
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
        when (item.itemId) {
//            R.id.settings ->
//                supportFragmentManager.beginTransaction()
//                    .replace(R.id.container, SettingsFragment()).addToBackStack("settings").commit()
            R.id.sync -> controller.restart()
            R.id.about -> AboutDialogFragment().show(supportFragmentManager, "AboutDialog")
            R.id.selfTest -> {
                lifecycleScope.launch(Dispatchers.IO) {
//                    //signing test
//                    val keyPair = Bbs.generateBls12381G2Key(ByteArray(0))
//                    val publicKey = keyPair.publicKey
//                    val secretKey = keyPair.secretKey
//                    val messages = Array(200) {
//                        Random.nextBytes(200)
//                    }
//                    val signature = Bbs.blsSign(secretKey, publicKey, messages)
//                    val isVerified = Bbs.blsVerify(publicKey, signature, messages)
//
//                    //serialization test
//                    val credential = Credential(
//                        context = listOf(URI.create("https://w3id.org/vaccination/v1")),
//                        type = listOf("VaccinationCertificate"),
//                        credentialSubject = mapOf(
//                            "type" to "VaccinationEvent",
//                            "batchNumber" to "1626382736",
//                            "dateOfVaccination" to "2021-06-23T13:40:12Z",
//                            "administeringCentre" to "Praxis Sommergarten",
//                            "healthProfessional" to "883110000015376",
//                            "countryOfVaccination" to "GE",
//                            "nextVaccinationDate" to "2021-08-16T13:40:12Z",
//                            "order" to "3/3",
//                            "recipient" to mapOf(
//                                "type" to "VaccineRecipient",
//                                "givenName" to "Marion",
//                                "familyName" to "Mustermann",
//                                "gender" to "Female",
//                                "birthDate" to "1961-08-17"
//                            ),
//                            "vaccine" to mapOf(
//                                "type" to "Vaccine",
//                                "atcCode" to "J07BX03",
//                                "medicinalProductName" to "COVID-19 Vaccine Moderna",
//                                "marketingAuthorizationHolder" to "Moderna Biotech"
//                            )
//                        ),
//                        issuanceDate = Date(),
//                        issuer = URI.create("did:key:test")
//                    )
//                    val serializedCredential = Json.encodeToString(credential)
//                    val isSerialized = credential.equals(Json.decodeFromString<Credential>(serializedCredential))
//                    val isCorrectJsonLD = credential.toDataset().size() == 24
//                    runOnUiThread {
//                        Toast.makeText(
//                            this@MainActivity,
//                            "signing: $isVerified - serialization: $isSerialized - JsonLD: $isCorrectJsonLD",
//                            Toast.LENGTH_LONG
//                        ).show()
//                    }
                }
            }
        }
        return false
    }
}