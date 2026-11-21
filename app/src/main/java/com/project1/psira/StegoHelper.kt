package com.project1.psira

import android.graphics.Bitmap
import android.graphics.Color

object StegoHelper {
    // High-entropy signature to identify PsiRa stego containers
    private val SIGNATURE = byteArrayOf(0xA7.toByte(), 0x3F.toByte(), 0x91.toByte(), 0x5E.toByte())
    private val LEGACY_SIGNATURE = byteArrayOf(0x50, 0x53, 0x52, 0x41) // "PSRA" legacy compatibility

    /**
     * Conceals [secretBytes] inside [sourceBitmap].
     * Returns a new Bitmap with the hidden message, or null if the image is too small.
     */
    fun conceal(sourceBitmap: Bitmap, secretBytes: ByteArray): Bitmap? {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val len = secretBytes.size
        val header = ByteArray(8)
        System.arraycopy(SIGNATURE, 0, header, 0, 4)
        header[4] = (len shr 24).toByte()
        header[5] = (len shr 16).toByte()
        header[6] = (len shr 8).toByte()
        header[7] = len.toByte()

        val fullPayload = header + secretBytes
        val totalBitsNeeded = fullPayload.size * 8

        // Each pixel has 3 color channels (R, G, B) to store 1 bit each
        val maxBits = width * height * 3
        if (totalBitsNeeded > maxBits) {
            return null // Bitmap is too small
        }

        // Copy source bitmap to a mutable ARGB_8888 bitmap
        val workingBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)

        for (bitIndex in 0 until totalBitsNeeded) {
            val bytePos = bitIndex / 8
            val bitPos = 7 - (bitIndex % 8)
            val bit = (fullPayload[bytePos].toInt() shr bitPos) and 1

            val pixelIndex = bitIndex / 3
            val channel = bitIndex % 3
            val x = pixelIndex % width
            val y = pixelIndex / width

            val pixel = workingBitmap.getPixel(x, y)
            val a = Color.alpha(pixel)
            var r = Color.red(pixel)
            var g = Color.green(pixel)
            var b = Color.blue(pixel)

            when (channel) {
                0 -> r = (r and 0xFE) or bit
                1 -> g = (g and 0xFE) or bit
                2 -> b = (b and 0xFE) or bit
            }

            workingBitmap.setPixel(x, y, Color.argb(a, r, g, b))
        }

        return workingBitmap
    }

    /**
     * Extracts hidden bytes from [stegoBitmap].
     * Returns null if no valid signature is found or length is invalid.
     */
    fun extract(stegoBitmap: Bitmap): ByteArray? {
        val width = stegoBitmap.width
        val height = stegoBitmap.height

        val maxBits = width * height * 3
        val headerSize = 8
        val headerBits = headerSize * 8

        if (maxBits < headerBits) return null

        val headerBytes = ByteArray(headerSize)

        // 1. Extract header bits
        for (bitIndex in 0 until headerBits) {
            val pixelIndex = bitIndex / 3
            val channel = bitIndex % 3
            val x = pixelIndex % width
            val y = pixelIndex / width

            val pixel = stegoBitmap.getPixel(x, y)
            val bit = when (channel) {
                0 -> Color.red(pixel) and 1
                1 -> Color.green(pixel) and 1
                else -> Color.blue(pixel) and 1
            }

            val bytePos = bitIndex / 8
            val bitPos = 7 - (bitIndex % 8)
            headerBytes[bytePos] = (headerBytes[bytePos].toInt() or (bit shl bitPos)).toByte()
        }

        // Verify Signature
        val matchesCurrent = (0 until 4).all { headerBytes[it] == SIGNATURE[it] }
        val matchesLegacy = (0 until 4).all { headerBytes[it] == LEGACY_SIGNATURE[it] }
        if (!matchesCurrent && !matchesLegacy) {
            return null // Signature mismatch
        }

        // Extract Length
        val len = ((headerBytes[4].toInt() and 0xFF) shl 24) or
                  ((headerBytes[5].toInt() and 0xFF) shl 16) or
                  ((headerBytes[6].toInt() and 0xFF) shl 8) or
                  (headerBytes[7].toInt() and 0xFF)

        if (len <= 0 || (headerSize + len) * 8 > maxBits) {
            return null // Invalid length or exceeds capacity
        }

        // 2. Extract full payload (header + secret)
        val totalBitsNeeded = (headerSize + len) * 8
        val fullPayload = ByteArray(headerSize + len)
        System.arraycopy(headerBytes, 0, fullPayload, 0, headerSize)

        for (bitIndex in headerBits until totalBitsNeeded) {
            val pixelIndex = bitIndex / 3
            val channel = bitIndex % 3
            val x = pixelIndex % width
            val y = pixelIndex / width

            val pixel = stegoBitmap.getPixel(x, y)
            val bit = when (channel) {
                0 -> Color.red(pixel) and 1
                1 -> Color.green(pixel) and 1
                else -> Color.blue(pixel) and 1
            }

            val bytePos = bitIndex / 8
            val bitPos = 7 - (bitIndex % 8)
            fullPayload[bytePos] = (fullPayload[bytePos].toInt() or (bit shl bitPos)).toByte()
        }

        val result = ByteArray(len)
        System.arraycopy(fullPayload, headerSize, result, 0, len)
        return result
    }
}
