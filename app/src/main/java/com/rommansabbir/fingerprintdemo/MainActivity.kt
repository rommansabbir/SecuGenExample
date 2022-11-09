package com.rommansabbir.fingerprintdemo

import SecuGen.FDxSDKPro.*
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.*
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.rommansabbir.fingerprintdemo.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), SGFingerPresentEvent {
    private lateinit var binding: ActivityMainBinding
    private var sgfplib: JSGFPLib? = null
    private var initError: Long = -1
    private var mPermissionIntent: PendingIntent? = null
/*    private var mRegisterImage: ByteArray? = null
    private var mVerifyImage: ByteArray? = null
    private var mRegisterTemplate: ByteArray? = null
    private var mVerifyTemplate: ByteArray? = null
    private var mMaxTemplateSize: IntArray? = null*/

    /*private var mImageWidth = 0
    private var mImageHeight = 0
    private var mImageDPI = 0*/
    private var grayBuffer: IntArray? = null
    private var grayBitmap: Bitmap? = null

    private var filter //2014-04-11
            : IntentFilter? = null
    private var autoOn: SGAutoOnEventNotifier? = null
    private var usbPermissionRequested = false
    private var bSecuGenDeviceOpened = false
    private var mFakeEngineReady: BooleanArray = BooleanArray(1)
    private var mNumFakeThresholds: IntArray = IntArray(1)
    private var mDefaultFakeThreshold: IntArray = IntArray(1)
    private var mFakeDetectionLevel = 1

    private val IMAGE_CAPTURE_TIMEOUT_MS = 10000
    private val IMAGE_CAPTURE_QUALITY = 50

    //This broadcast receiver is necessary to get user permissions to access the attached USB device
    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    private val mUsbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            //Log.d(TAG,"Enter mUsbReceiver.onReceive()");
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device =
                        intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice?
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            //DEBUG Log.d(TAG, "Vendor ID : " + device.getVendorId() + "\n");
                            //DEBUG Log.d(TAG, "Product ID: " + device.getProductId() + "\n");
                        } else {
                        }
                    } else {
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        sgfplib?.CloseDevice()
        sgfplib?.Close()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Init
        /*initRequired()*/


        //USB Permissions
        mPermissionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_USB_PERMISSION),
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_ONE_SHOT
        )
        filter = IntentFilter(ACTION_USB_PERMISSION)
        sgfplib = JSGFPLib(this, getSystemService(USB_SERVICE) as UsbManager)
        bSecuGenDeviceOpened = false
        usbPermissionRequested = false
        autoOn = SGAutoOnEventNotifier(sgfplib, this)
        Log.d(this::class.java.canonicalName, "Exit onCreate()")


        // Btn actions
        binding.button.setOnClickListener { captureFingerprint() }
        binding.button2.setOnClickListener { VerifyFingerPrint() }
    }

    private fun initRequired() {
        grayBuffer =
            IntArray(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES * JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES)
        for (i in grayBuffer!!.indices) {
            grayBuffer!![i] = Color.GRAY
        }
        grayBitmap = Bitmap.createBitmap(
            JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES,
            JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES,
            Bitmap.Config.ARGB_8888
        )
        grayBitmap?.setPixels(
            grayBuffer,
            0,
            JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES,
            0,
            0,
            JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES,
            JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES
        )
        val sintbuffer =
            IntArray(JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES / 2 * (JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES / 2))
        for (i in sintbuffer.indices) sintbuffer[i] = Color.GRAY
        val sb = Bitmap.createBitmap(
            JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES / 2,
            JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES / 2,
            Bitmap.Config.ARGB_8888
        )
        sb.setPixels(
            sintbuffer,
            0,
            JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES / 2,
            0,
            0,
            JSGFPLib.MAX_IMAGE_WIDTH_ALL_DEVICES / 2,
            JSGFPLib.MAX_IMAGE_HEIGHT_ALL_DEVICES / 2
        )
        /*mMaxTemplateSize = IntArray(1)*/
    }

    override fun onResume() {
        super.onResume()
        // Register USB Checker
        var error = (sgfplib?.Init(SGFDxDeviceName.SG_DEV_AUTO))
        when (error != SGFDxErrorCode.SGFDX_ERROR_NONE) {
            true -> {
                // Fingerprint device not supported for this device
                val dlgAlert = AlertDialog.Builder(this)
                if (error == SGFDxErrorCode.SGFDX_ERROR_DEVICE_NOT_FOUND) dlgAlert.setMessage("The attached fingerprint device is not supported on Android") else dlgAlert.setMessage(
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
            }
            else -> {
                lifecycleScope.launch {
                    sgfplib?.GetUsbDevice()?.let { it ->
                        // Check for connected device
                        var hasPermission = sgfplib?.GetUsbManager()?.hasPermission(it) ?: false

                        if (!hasPermission) {
                            if (!usbPermissionRequested) {
                                /*debugMessage("Requesting USB Permission\n")*/
                                //Log.d(TAG, "Call GetUsbManager().requestPermission()");
                                usbPermissionRequested = true
                                sgfplib!!.GetUsbManager()
                                    .requestPermission(it, mPermissionIntent)
                            } else {
                                //wait up to 20 seconds for the system to grant USB permission
                                hasPermission = sgfplib!!.GetUsbManager().hasPermission(it)
                                /*debugMessage("Waiting for USB Permission\n")*/
                                lifecycleScope.launch {
                                    var i = 0
                                    while (!hasPermission && i <= 40) {
                                        ++i
                                        hasPermission =
                                            sgfplib!!.GetUsbManager().hasPermission(it)
                                        try {
                                            delay(500)
                                        } catch (e: InterruptedException) {
                                            e.printStackTrace()
                                        }
                                        //Log.d(TAG, "Waited " + i*50 + " milliseconds for USB permission");
                                    }
                                }
                            }
                        }

                        if (hasPermission) {
                            error = sgfplib!!.OpenDevice(0)
                            if (error == SGFDxErrorCode.SGFDX_ERROR_NONE) {
                                bSecuGenDeviceOpened = true
                                //inti device info
                                sgfplib?.let { APFingerprintHelper.init(it) }
                                autoOn!!.start()
                            } else {
                                // Waiting for Permission
                                /*debugMessage("Waiting for USB Permission\n")*/
                            }
                        }
                    } ?: kotlin.run {
                        // Fingerprint device not found
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
                }
            }
        }
    }

    override fun onPause() {
        try {
            autoOn?.stop()
            sgfplib?.CloseDevice()
            unregisterReceiver(mUsbReceiver)
        } catch (e: Exception) {

        }

/*        mRegisterImage = null
        mVerifyImage = null
        mRegisterTemplate = null
        mVerifyTemplate = null*/
        super.onPause()
    }

    override fun SGFingerPresentCallback() {
        autoOn?.stop()
    }

    private var ext : ByteArray? = null
    private fun captureFingerprint() {
        lifecycleScope.launch {
            ext = null
            APFingerprintHelper.readFingerprint {
                when {
                    it.errorMessage.isNullOrEmpty() -> {
                        // Success
                        binding.mImageViewFingerprint.setImageBitmap(it.bitmap)
                        ext = it.template
                    }
                    else -> {
                        // Error
                        Toast.makeText(this@MainActivity, it.errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }


            /*// Create a byte[] to store the image
            val buffer = ByteArray(APFingerprintHelper.width * APFingerprintHelper.height)
            val getImageError = sgfplib?.GetImage(buffer)
            if (getImageError != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                Toast.makeText(
                    this@MainActivity,
                    "Error while reading image from Scanner",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            // Set the byte[] as Bitmap
            binding.mImageViewFingerprint.setImageBitmap(
                toGrayscale(
                    buffer,
                    APFingerprintHelper.width,
                    APFingerprintHelper.height
                )
            )

            //Create template from the image
            sgfplib?.GetMaxTemplateSize(APFingerprintHelper.maxTemplateSize)
            val minBuffer = ByteArray(APFingerprintHelper.maxTemplateSize[0])

            val quality = IntArray(1)
            val imageQualityError = sgfplib!!.GetImageQuality(
                APFingerprintHelper.width.toLong(),
                APFingerprintHelper.height.toLong(),
                buffer,
                quality
            )
            if (imageQualityError != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                Toast.makeText(
                    this@MainActivity,
                    "Error while getting image quality",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            // Set the information about template
            val templateError =
                sgfplib?.CreateTemplate(
                    APFingerprintHelper.getFingerInfo(quality[0]),
                    buffer,
                    minBuffer
                )
            if (templateError != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                Toast.makeText(
                    this@MainActivity,
                    "Error while creating template",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            Log.d(this::class.java.canonicalName, "captureFingerprint: $templateError")*/
        }
    }

    fun VerifyFingerPrint() {
        lifecycleScope.launch {
            ext?.let { bytes ->
                APFingerprintHelper.verifyFingerprint(bytes){
                    try {
                        when {
                            it.errorMessage.isNullOrEmpty() -> {
                                // Success
                                Toast.makeText(this@MainActivity, it.errorMessage, Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                // Error
                                Toast.makeText(this@MainActivity, it.errorMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    catch (e : Exception){
                        Toast.makeText(this@MainActivity, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }

                }
            }
        }
        /*var dwTimeStart: Long = 0
        var dwTimeEnd: Long = 0
        var dwTimeElapsed: Long = 0
*//*        if (!bFingerprintRegistered) {
            *//**//*mTextViewResult.setText("Please Register a finger")
            sgfplib!!.SetLedOn(false)*//**//*
            return
        }*//*
        if (mVerifyImage != null) mVerifyImage = null
        mVerifyImage = ByteArray(APFingerprintHelper.width * APFingerprintHelper.height)
        dwTimeStart = System.currentTimeMillis()
        var result = sgfplib!!.GetImageEx(
            mVerifyImage,
            IMAGE_CAPTURE_TIMEOUT_MS.toLong(), IMAGE_CAPTURE_QUALITY.toLong()
        )
        *//*DumpFile("verify.raw", mVerifyImage)*//*
        dwTimeEnd = System.currentTimeMillis()
        dwTimeElapsed = dwTimeEnd - dwTimeStart
        *//*mImageViewFingerprint.setImageBitmap(toGrayscale(mVerifyImage))
        mImageViewVerify.setImageBitmap(toGrayscale(mVerifyImage))*//*
        dwTimeStart = System.currentTimeMillis()
        result = sgfplib!!.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794)
        dwTimeEnd = System.currentTimeMillis()
        dwTimeElapsed = dwTimeEnd - dwTimeStart
        val quality = IntArray(1)
        result = sgfplib!!.GetImageQuality(
            APFingerprintHelper.width.toLong(),
            APFingerprintHelper.height.toLong(),
            mVerifyImage,
            quality
        )
        var fpInfo: SGFingerInfo? = SGFingerInfo()
        fpInfo!!.FingerNumber = SG_FINGPOS_LI
        fpInfo.ImageQuality = quality[0]
        fpInfo.ImpressionType = SG_IMPTYPE_LP
        fpInfo.ViewNumber = 1
        for (i in mVerifyTemplate!!.indices) mVerifyTemplate!![i] = 0
        dwTimeStart = System.currentTimeMillis()
        result = sgfplib!!.CreateTemplate(fpInfo, mVerifyImage, mVerifyTemplate)
        *//*DumpFile("verify.min", mVerifyTemplate)*//*
        dwTimeEnd = System.currentTimeMillis()
        dwTimeElapsed = dwTimeEnd - dwTimeStart
        if (result == SGFDxErrorCode.SGFDX_ERROR_NONE) {
            val size = IntArray(1)
            result = sgfplib!!.GetTemplateSize(mVerifyTemplate, size)
            var matched: BooleanArray? = BooleanArray(1)
            dwTimeStart = System.currentTimeMillis()
            result = sgfplib!!.MatchTemplate(
                mRegisterTemplate,
                mVerifyTemplate,
                SGFDxSecurityLevel.SL_NORMAL,
                matched
            )
            dwTimeEnd = System.currentTimeMillis()
            dwTimeElapsed = dwTimeEnd - dwTimeStart
            if (matched?.get(0) != null) {
                // Matched
                Toast.makeText(this, "Matched", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Not Matched", Toast.LENGTH_SHORT).show()
                // Not Matched
            }
            matched = null
        } else *//*mTextViewResult.setText("Fingerprint template extraction failed.")*//* {
            Toast.makeText(this, "Fingerprint template extraction failed.", Toast.LENGTH_SHORT)
                .show()
        }
        mVerifyImage = null
        fpInfo = null*/
    }

}