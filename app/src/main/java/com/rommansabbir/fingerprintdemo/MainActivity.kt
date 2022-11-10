package com.rommansabbir.fingerprintdemo

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.rommansabbir.fingerprintdemo.databinding.ActivityMainBinding
import com.rommansabbir.secugensdk.FingerprintManager
import com.rommansabbir.secugensdk.FingerprintManagerImpl
import com.rommansabbir.secugensdk.FingerprintTemplateType
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var fingerprintManager: FingerprintManager
    private lateinit var binding: ActivityMainBinding
    private val templateType: FingerprintTemplateType = FingerprintTemplateType.TemplateISO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        //
        fingerprintManager = FingerprintManagerImpl()
        fingerprintManager.onCreated(this)

        binding.button.setOnClickListener { captureFingerprint() }
        binding.button2.setOnClickListener { verifyFingerPrint() }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            fingerprintManager.onResume(
                this@MainActivity, templateType,
                {
                    // Fingerprint device not supported for this device
                    val dlgAlert = AlertDialog.Builder(this@MainActivity)
                    if (it) dlgAlert.setMessage("The attached fingerprint device is not supported on Android") else dlgAlert.setMessage(
                        "Fingerprint device initialization failed!"
                    )
                    dlgAlert.setTitle("SecuGen Fingerprint SDK")
                    dlgAlert.setPositiveButton("OK",
                        DialogInterface.OnClickListener { dialog, whichButton ->
                            finish()
                            return@OnClickListener
                        }
                    )
                    dlgAlert.setCancelable(false)
                    dlgAlert.create().show()
                },
                {
                    val dlgAlert = AlertDialog.Builder(this@MainActivity)
                    dlgAlert.setMessage("SecuGen fingerprint sensor not found!")
                    dlgAlert.setTitle("SecuGen Fingerprint SDK")
                    dlgAlert.setPositiveButton("OK",
                        DialogInterface.OnClickListener { dialog, whichButton ->
                            finish()
                            return@OnClickListener
                        }
                    )
                    dlgAlert.setCancelable(false)
                    dlgAlert.create().show()
                }
            )
        }
    }

    override fun onPause() {
        fingerprintManager.onPause(this)
        super.onPause()
    }

    override fun onStop() {
        fingerprintManager.onPause(this)
        super.onStop()
    }

    override fun onDestroy() {
        fingerprintManager.onDestroy(this)
        super.onDestroy()
    }


    private var ext: ByteArray? = null

    private fun captureFingerprint() {
        lifecycleScope.launch {
            ext = null
            val result = fingerprintManager.readFingerprint(templateType)
            if (result.second != null) {
                Toast.makeText(
                    this@MainActivity,
                    result.second?.localizedMessage?.toString(),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            if (result.first == null) {
                Toast.makeText(
                    this@MainActivity,
                    "Scan result not found",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            ext = result.first!!.template
            binding.mImageViewFingerprint.setImageBitmap(result.first!!.bitmap)
        }
    }

    private fun verifyFingerPrint() {
        lifecycleScope.launch {
            ext?.let { bytes ->
                val result = fingerprintManager.verifyFingerprint(
                    bytes,
                    templateType
                )
                if (result.second != null) {
                    Toast.makeText(
                        this@MainActivity,
                        result.second?.localizedMessage?.toString(),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                Toast.makeText(this@MainActivity, "Matched : ${result.first}", Toast.LENGTH_SHORT)
                    .show()
            }
        }

    }
}