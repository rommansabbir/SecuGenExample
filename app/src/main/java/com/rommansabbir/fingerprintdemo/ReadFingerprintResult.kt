package com.rommansabbir.fingerprintdemo

import android.graphics.Bitmap

class ReadFingerprintResult(
    val template: ByteArray?,
    val scannedImage : ByteArray?,
    val bitmap: Bitmap?,
    override val errorMessage: String? = null
) : FingerprintResult(errorMessage)

open class FingerprintResult(open val errorMessage: String?)