package com.rommansabbir.secugensdk

import android.graphics.Bitmap

data class ReadFingerprintResult(
    val template: ByteArray,
    val bitmap: Bitmap,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReadFingerprintResult

        if (!template.contentEquals(other.template)) return false
        if (bitmap != other.bitmap) return false

        return true
    }

    override fun hashCode(): Int {
        var result = template.contentHashCode()
        result = 31 * result + bitmap.hashCode()
        return result
    }
}