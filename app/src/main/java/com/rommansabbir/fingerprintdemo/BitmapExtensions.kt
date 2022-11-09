package com.rommansabbir.fingerprintdemo

import android.graphics.Bitmap
import java.nio.ByteBuffer

fun toGrayscale(mImageBuffer: ByteArray, mImageWidth: Int, mImageHeight: Int): Bitmap? {
    return try {
        val bits = ByteArray(mImageBuffer.size * 4)
        for (i in mImageBuffer.indices) {
            bits[i * 4 + 2] = mImageBuffer[i]
            bits[i * 4 + 1] = bits[i * 4 + 2]
            bits[i * 4] = bits[i * 4 + 1] // Invert the source bits
            bits[i * 4 + 3] = -1 // 0xff, that's the alpha.
        }
        val bmpGrayscale = Bitmap.createBitmap(mImageWidth, mImageHeight, Bitmap.Config.ARGB_8888)
        bmpGrayscale.copyPixelsFromBuffer(ByteBuffer.wrap(bits))
        bmpGrayscale
    } catch (e: Exception) {
        null
    }
}