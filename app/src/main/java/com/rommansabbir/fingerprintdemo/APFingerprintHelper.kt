package com.rommansabbir.fingerprintdemo

import SecuGen.FDxSDKPro.*
import SecuGen.FDxSDKPro.SGFDxSecurityLevel.SL_NORMAL

object APFingerprintHelper {
    @Volatile
    private var jsgfpLib: JSGFPLib? = null

    @Volatile
    var isInitialized: Boolean = false
        private set
    var height: Int = 0
        private set
    var width: Int = 0
        private set

    var dpi: Int = 0
        private set

    var maxTemplateSize: IntArray = IntArray(1)
        private set

    var verityTemplate: ByteArray = ByteArray(maxTemplateSize[0])
        private set

    fun init(jsgfpLib: JSGFPLib): Boolean {
        synchronized(Any()) {
            if (destroy()) {
                this.jsgfpLib = jsgfpLib
                this.jsgfpLib?.SetBrightness(100)
                this.jsgfpLib?.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378)
                this.jsgfpLib?.GetMaxTemplateSize(maxTemplateSize)
                val deviceInfo = SGDeviceInfoParam()
                if (!isInitialized) {
                    val res = jsgfpLib.GetDeviceInfo(deviceInfo)
                    if (res == SGFDxErrorCode.SGFDX_ERROR_NONE) {
                        height = deviceInfo.imageHeight
                        width = deviceInfo.imageWidth
                        dpi = deviceInfo.imageDPI

                        //
                        isInitialized = true
                    }
                }
                return isInitialized
            } else {
                return false
            }
        }
    }

    fun destroy(): Boolean {
        this.isInitialized = false
        this.jsgfpLib = null
        this.dpi = 0
        this.height = 0
        this.width = 0
        return true
    }

    private fun getFingerInfo(quality: Int): SGFingerInfo {
        val fingerInfo = SGFingerInfo()
        fingerInfo.FingerNumber = SGFingerPosition.SG_FINGPOS_RL
        fingerInfo.ImageQuality = quality
        fingerInfo.ImpressionType = SGImpressionType.SG_IMPTYPE_LP
        fingerInfo.ViewNumber = 1
        return fingerInfo
    }

    fun readFingerprint(onResult: (ReadFingerprintResult) -> Unit) {
        tryCatch(
            {
                if (!isInitialized) {
                    ReadFingerprintResult(
                        template = null,
                        bitmap = null,
                        scannedImage = null,
                        errorMessage = "Not initialized"
                    )
                }
                // Create a byte[] to store the image
                val imageBuffer = ByteArray(width * height)
                val getImageError = jsgfpLib?.GetImage(imageBuffer)
                if (getImageError != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                    onResult.invoke(
                        ReadFingerprintResult(
                            template = null,
                            bitmap = null,
                            scannedImage = null,
                            errorMessage = "Error while reading image from Scanner"
                        )
                    )
                    return@tryCatch
                }

                //Create template from the image
                jsgfpLib?.GetMaxTemplateSize(maxTemplateSize)
                val templateBuffer = ByteArray(maxTemplateSize[0])

                val quality = IntArray(1)
                val imageQualityError = jsgfpLib?.GetImageQuality(
                    width.toLong(),
                    height.toLong(),
                    imageBuffer,
                    quality
                )
                if (imageQualityError != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                    onResult.invoke(
                        ReadFingerprintResult(
                            template = null,
                            bitmap = null,
                            scannedImage = null,
                            errorMessage = "Error while getting image quality"
                        )
                    )
                    return@tryCatch
                }

                // Set the information about template
                val templateError =
                    jsgfpLib?.CreateTemplate(
                        getFingerInfo(quality[0]),
                        imageBuffer,
                        templateBuffer
                    )
                if (templateError != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                    onResult.invoke(
                        ReadFingerprintResult(
                            template = null,
                            bitmap = null,
                            scannedImage = null,
                            errorMessage = "Error while creating template"
                        )
                    )
                    return@tryCatch
                }

                // Verify image quality
                val qualityVerify = IntArray(1)
                val verifyError = jsgfpLib?.GetImageQuality(
                    width.toLong(),
                    height.toLong(),
                    imageBuffer,
                    qualityVerify
                )
                if (verifyError != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                    onResult.invoke(
                        ReadFingerprintResult(
                            template = null,
                            bitmap = null,
                            scannedImage = null,
                            errorMessage = "Image quality is too low. Scan again."
                        )
                    )
                    return@tryCatch
                }
                if (qualityVerify[0] < 60) {
                    onResult.invoke(
                        ReadFingerprintResult(
                            template = null,
                            bitmap = null,
                            scannedImage = null,
                            errorMessage = "Image quality is too low. Scan again."
                        )
                    )
                } else {
                    // Return the success result
                    onResult.invoke(
                        ReadFingerprintResult(
                            template = templateBuffer, bitmap = toGrayscale(
                                imageBuffer,
                                width,
                                height
                            ),
                            scannedImage = imageBuffer,
                            errorMessage = null
                        )
                    )
                }
            },
            onResult
        )
    }

    fun verifyFingerprint(existingTemplate: ByteArray, onResult: (ReadFingerprintResult) -> Unit) {
        tryCatch(
            {
                jsgfpLib?.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_ANSI378)

                var currentSnap: ByteArray? = null
                readFingerprint {
                    if (it.errorMessage.isNullOrEmpty()) {
                        currentSnap = it.template
                    } else {
                        onResult.invoke(it)
                        return@readFingerprint
                    }
                }

                val matched = BooleanArray(1)
                matched[0] = false
                val sampleInfo = SGANSITemplateInfo()
                val error = jsgfpLib?.GetAnsiTemplateInfo(existingTemplate, sampleInfo)

                if (error != SGFDxErrorCode.SGFDX_ERROR_NONE || error.toInt() == -1) {
                    onResult.invoke(
                        ReadFingerprintResult(
                            template = null,
                            scannedImage = null,
                            bitmap = null,
                            errorMessage = "Failed to get ANSI template for matching process"
                        )
                    )
                    return@tryCatch
                }

                var found = false
                for (item in 0..sampleInfo.TotalSamples) {
                    if (sampleInfo.SampleInfo[item].FingerNumber == SGFingerPosition.SG_FINGPOS_RL) {
                        found = true
                        matched[0] = true
                        val matchError = jsgfpLib?.MatchAnsiTemplate(
                            existingTemplate,
                            item.toLong(),
                            currentSnap,
                            item.toLong(),
                            SL_NORMAL,
                            matched
                        )
                    }
                    if (found) {
                        break
                    }
                }
                if (matched[0]) {
                    onResult.invoke(
                        ReadFingerprintResult(
                            template = null,
                            scannedImage = null,
                            bitmap = null,
                            errorMessage = "Matched"
                        )
                    )
                    return@tryCatch
                } else {
                    onResult.invoke(
                        ReadFingerprintResult(
                            template = null,
                            scannedImage = null,
                            bitmap = null,
                            errorMessage = "Not matched"
                        )
                    )
                    return@tryCatch
                }
            },
            onResult
        )
    }

    private fun tryCatch(tryBlock: () -> Unit, onResult: (ReadFingerprintResult) -> Unit) {
        try {
            tryBlock.invoke()
        } catch (e: Exception) {
            onResult.invoke(
                ReadFingerprintResult(
                    template = null,
                    scannedImage = null,
                    bitmap = null,
                    errorMessage = e.localizedMessage
                )
            )
        }
    }
}
