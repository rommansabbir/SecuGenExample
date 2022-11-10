package com.rommansabbir.secugensdk

import SecuGen.FDxSDKPro.*
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.USB_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Parcelable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

interface FingerprintManager {
    fun onCreated(activity: Activity)
    suspend fun onResume(
        activity: Activity,
        templateType: FingerprintTemplateType,
        onDeviceNotFound: (isFound: Boolean) -> Unit,
        onSenorNotFound: () -> Unit
    )

    fun onPause(activity: Activity)
    fun onDestroy(activity: Activity)
    fun readFingerprint(templateType: FingerprintTemplateType): Pair<ReadFingerprintResult?, Exception?>
    fun verifyFingerprint(
        matchingTemplate: ByteArray,
        templateType: FingerprintTemplateType
    ): Pair<Boolean, Exception?>
}

class FingerprintManagerImpl : FingerprintManager, SGFingerPresentEvent {
    var height: Int = 0
        private set
    var width: Int = 0
        private set

    var dpi: Int = 0
        private set

    var maxTemplateSize: IntArray = IntArray(1)
        private set

    private var sgfplib: JSGFPLib? = null
    private var filter: IntentFilter? = null
    private var autoOn: SGAutoOnEventNotifier? = null
    private var usbPermissionRequested = false
    private var bSecuGenDeviceOpened = false
    private var mPermissionIntent: PendingIntent? = null


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


    override fun onCreated(activity: Activity) {
        //USB Permissions
        mPermissionIntent = PendingIntent.getBroadcast(
            activity,
            0,
            Intent(ACTION_USB_PERMISSION),
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_ONE_SHOT
        )
        filter = IntentFilter(ACTION_USB_PERMISSION)
        sgfplib = JSGFPLib(activity, activity.getSystemService(USB_SERVICE) as UsbManager)
        bSecuGenDeviceOpened = false
        usbPermissionRequested = false
        autoOn = SGAutoOnEventNotifier(sgfplib, this)
    }

    override suspend fun onResume(
        activity: Activity,
        templateType: FingerprintTemplateType,
        onDeviceNotFound: (isFound: Boolean) -> Unit,
        onSenorNotFound: () -> Unit
    ) {
        // Register USB Checker
        var error = (sgfplib?.Init(SGFDxDeviceName.SG_DEV_AUTO))
        when (error != SGFDxErrorCode.SGFDX_ERROR_NONE) {
            true -> {
                onDeviceNotFound.invoke(error == SGFDxErrorCode.SGFDX_ERROR_DEVICE_NOT_FOUND)
            }
            else -> {
                sgfplib?.GetUsbDevice()?.let { it ->
                    // Check for connected device
                    var hasPermission = sgfplib?.GetUsbManager()?.hasPermission(it) ?: false

                    if (!hasPermission) {
                        if (!usbPermissionRequested) {
                            usbPermissionRequested = true
                            sgfplib!!.GetUsbManager()
                                .requestPermission(it, mPermissionIntent)
                        } else {
                            //wait up to 20 seconds for the system to grant USB permission
                            hasPermission = sgfplib!!.GetUsbManager().hasPermission(it)
                            withContext(Dispatchers.Default) {
                                var i = 0
                                while (!hasPermission && i <= 40) {
                                    ++i
                                    hasPermission =
                                        sgfplib!!.GetUsbManager().hasPermission(it)
                                    delay(500)
                                }
                            }
                        }
                    }

                    if (hasPermission) {
                        error = sgfplib!!.OpenDevice(0)
                        if (error == SGFDxErrorCode.SGFDX_ERROR_NONE) {
                            bSecuGenDeviceOpened = true

                            sgfplib?.SetBrightness(100)
                            setTemplate(templateType)
                            sgfplib?.GetMaxTemplateSize(maxTemplateSize)
                            val deviceInfo = SGDeviceInfoParam()
                            if (sgfplib != null) {
                                val res = sgfplib?.GetDeviceInfo(deviceInfo)
                                if (res == SGFDxErrorCode.SGFDX_ERROR_NONE) {
                                    height = deviceInfo.imageHeight
                                    width = deviceInfo.imageWidth
                                    dpi = deviceInfo.imageDPI
                                }
                            }

                            autoOn!!.start()
                        } else {
                            // Waiting for Permission
                        }
                    }
                } ?: kotlin.run {
                    // Fingerprint device not found
                    onSenorNotFound.invoke()
                }
            }
        }
    }

    private fun setTemplate(templateType: FingerprintTemplateType) {
        sgfplib?.SetTemplateFormat(
            FingerprintTemplateType.Parser.parse(templateType)
        )
    }

    override fun onPause(activity: Activity) {
        try {
            autoOn?.stop()
            sgfplib?.CloseDevice()
            activity.unregisterReceiver(mUsbReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy(activity: Activity) {
        try {
            sgfplib?.CloseDevice()
            sgfplib?.Close()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun getFingerInfo(quality: Int): SGFingerInfo {
        val fingerInfo = SGFingerInfo()
        fingerInfo.FingerNumber = SGFingerPosition.SG_FINGPOS_RL
        fingerInfo.ImageQuality = quality
        fingerInfo.ImpressionType = SGImpressionType.SG_IMPTYPE_LP
        fingerInfo.ViewNumber = 1
        return fingerInfo
    }

    override fun readFingerprint(templateType: FingerprintTemplateType): Pair<ReadFingerprintResult?, Exception?> {
        if (sgfplib == null) {
            return Pair(null, Exception("Not initialized"))
        }
        try {
            setTemplate(templateType)
            // Create a byte[] to store the image
            val imageBuffer = ByteArray(width * height)
            val getImageError = sgfplib?.GetImage(imageBuffer)
            if (getImageError != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                return Pair(null, Exception("Error while reading image from Scanner"))
            }

            //Create template from the image
            sgfplib?.GetMaxTemplateSize(maxTemplateSize)
            val templateBuffer = ByteArray(maxTemplateSize[0])

            val quality = IntArray(1)
            val imageQualityError = sgfplib?.GetImageQuality(
                width.toLong(),
                height.toLong(),
                imageBuffer,
                quality
            )
            if (imageQualityError != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                return Pair(null, Exception("Error while getting image quality"))
            }

            // Set the information about template
            val templateError =
                sgfplib?.CreateTemplate(
                    getFingerInfo(quality[0]),
                    imageBuffer,
                    templateBuffer
                )
            if (templateError != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                return Pair(null, Exception("Error while creating template"))
            }

            // Verify image quality
            val qualityVerify = IntArray(1)
            val verifyError = sgfplib?.GetImageQuality(
                width.toLong(),
                height.toLong(),
                imageBuffer,
                qualityVerify
            )
            if (verifyError != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                return Pair(null, Exception("Image quality is too low. Scan again."))
            }
            return if (qualityVerify[0] < 80) {
                Pair(null, Exception("Image quality is too low. Scan again."))
            } else {
                // Return the success result
                Pair(
                    ReadFingerprintResult(
                        template = templateBuffer,
                        bitmap = toGrayscale(
                            imageBuffer,
                            width,
                            height
                        )!!
                    ),
                    null
                )
            }
        } catch (e: RuntimeException) {
            e.printStackTrace()
            return Pair(null, e)
        }
    }

    override fun verifyFingerprint(
        matchingTemplate: ByteArray,
        templateType: FingerprintTemplateType
    ): Pair<Boolean, Exception?> {
        val scanResult = readFingerprint(templateType)
        if (scanResult.second != null) {
            return Pair(false, scanResult.second)
        }
        if (scanResult.first == null) {
            return Pair(false, Exception("Verification Error"))
        }
        return when (templateType) {
            FingerprintTemplateType.TemplateANSI -> verifyFingerprintANSI(
                matchingTemplate,
                scanResult.first!!.template
            )
            FingerprintTemplateType.TemplateISO -> verifyFingerprintISO(
                matchingTemplate,
                scanResult.first!!.template
            )
        }
    }

    private fun verifyFingerprintANSI(
        template1: ByteArray,
        template2: ByteArray
    ): Pair<Boolean, Exception?> {
        if (sgfplib == null) {
            return Pair(false, Exception("Not initialized"))
        }
        try {
            val matched = BooleanArray(1)
            matched[0] = false
            val sampleInfo = SGANSITemplateInfo()
            val error = sgfplib?.GetAnsiTemplateInfo(template1, sampleInfo)

            if (error != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                return Pair(
                    false,
                    Exception("Failed to get ANSI template for matching process")
                )
            }

            var found = false
            for (item in 0..sampleInfo.TotalSamples) {
                if (sampleInfo.SampleInfo[item].FingerNumber == SGFingerPosition.SG_FINGPOS_RL) {
                    found = true
                    matched[0] = true
                    val matchError = sgfplib?.MatchAnsiTemplate(
                        template1,
                        item.toLong(),
                        template2,
                        item.toLong(),
                        SGFDxSecurityLevel.SL_NORMAL,
                        matched
                    )
                    println(matchError)
                }
                if (found) {
                    break
                }
            }
            return Pair(matched[0], null)
        } catch (e: RuntimeException) {
            return Pair(false, e)
        }
    }

    private fun verifyFingerprintISO(
        template1: ByteArray,
        template2: ByteArray
    ): Pair<Boolean, Exception?> {
        if (sgfplib == null) {
            return Pair(false, Exception("Not initialized"))
        }
        try {
            val matched = BooleanArray(1)
            matched[0] = false

            val sampleInfo = SGISOTemplateInfo()
            val templateError = sgfplib?.GetIsoTemplateInfo(template1, sampleInfo)
            if (templateError != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                return Pair(false, Exception("Failed to get ISO template for matching process"))
            }

            var found = false
            for (item in 0..sampleInfo.TotalSamples) {
                if (sampleInfo.SampleInfo[item].FingerNumber == SGFingerPosition.SG_FINGPOS_RL) {
                    found = true
                    matched[0] = true
                    val matchError = sgfplib?.MatchIsoTemplate(
                        template1,
                        item.toLong(),
                        template2,
                        item.toLong(),
                        SGFDxSecurityLevel.SL_NORMAL,
                        matched
                    )
                    println(matchError)
                }
                if (found) {
                    break
                }
            }
            return Pair(matched[0], null)
        } catch (e: Exception) {
            return Pair(false, e)
        }
    }

    override fun SGFingerPresentCallback() {
        autoOn?.stop()
    }

}