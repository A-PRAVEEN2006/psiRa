package com.project1.psira

import android.graphics.Bitmap
import android.graphics.Color

object StegoHelper {
    // Signature to identify that the image contains PsiRa hidden content ("PSRA")
    private val SIGNATURE = byteArrayOf(0x50, 0x53, 0x52, 0x41)

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

        var bitIndex = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (bitIndex >= totalBitsNeeded) {
                    return workingBitmap
                }

                val pixel = workingBitmap.getPixel(x, y)
                var r = Color.red(pixel)
                var g = Color.green(pixel)
                var b = Color.blue(pixel)
                val a = Color.alpha(pixel)

                // Embed 1 bit in Red channel
                if (bitIndex < totalBitsNeeded) {
                    val bytePos = bitIndex / 8
                    val bitPos = 7 - (bitIndex % 8)
                    val bit = (fullPayload[bytePos].toInt() shr bitPos) and 1
                    r = (r and 0xFE) or bit
                    bitIndex++
                }

                // Embed 1 bit in Green channel
                if (bitIndex < totalBitsNeeded) {
                    val bytePos = bitIndex / 8
                    val bitPos = 7 - (bitIndex % 8)
                    val bit = (fullPayload[bytePos].toInt() shr bitPos) and 1
                    g = (g and 0xFE) or bit
                    bitIndex++
                }

                // Embed 1 bit in Blue channel
                if (bitIndex < totalBitsNeeded) {
                    val bytePos = bitIndex / 8
                    val bitPos = 7 - (bitIndex % 8)
                    val bit = (fullPayload[bytePos].toInt() shr bitPos) and 1
                    b = (b and 0xFE) or bit
                    bitIndex++
                }

                workingBitmap.setPixel(x, y, Color.argb(a, r, g, b))
            }
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

        val headerSize = 8
        val headerBits = headerSize * 8

        val headerBytes = ByteArray(headerSize)
        var bitIndex = 0

        // Extract header
        outer@ for (y in 0 until height) {
            for (x in 0 until width) {
                if (bitIndex >= headerBits) break@outer

                val pixel = stegoBitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Red
                if (bitIndex < headerBits) {
                    val bytePos = bitIndex / 8
                    val bitPos = 7 - (bitIndex % 8)
                    val bit = r and 1
                    headerBytes[bytePos] = (headerBytes[bytePos].toInt() or (bit shl bitPos)).toByte()
                    bitIndex++
                }

                // Green
                if (bitIndex < headerBits) {
                    val bytePos = bitIndex / 8
                    val bitPos = 7 - (bitIndex % 8)
                    val bit = g and 1
                    headerBytes[bytePos] = (headerBytes[bytePos].toInt() or (bit shl bitPos)).toByte()
                    bitIndex++
                }

                // Blue
                if (bitIndex < headerBits) {
                    val bytePos = bitIndex / 8
                    val bitPos = 7 - (bitIndex % 8)
                    val bit = b and 1
                    headerBytes[bytePos] = (headerBytes[bytePos].toInt() or (bit shl bitPos)).toByte()
                    bitIndex++
                }
            }
        }

        // Verify Signature
        for (i in 0 until 4) {
            if (headerBytes[i] != SIGNATURE[i]) {
                return null // Signature mismatch
            }
        }

        // Extract Length
        val len = ((headerBytes[4].toInt() and 0xFF) shl 24) or
                  ((headerBytes[5].toInt() and 0xFF) shl 16) or
                  ((headerBytes[6].toInt() and 0xFF) shl 8) or
                  (headerBytes[7].toInt() and 0xFF)

        if (len <= 0 || len > width * height * 3) {
            return null // Invalid length
        }

        // Extract full payload
        val totalBitsNeeded = (headerSize + len) * 8
        val fullPayload = ByteArray(headerSize + len)
        System.arraycopy(headerBytes, 0, fullPayload, 0, headerSize)

        bitIndex = headerBits
        val startPixel = headerBits / 3
        var currentPixel = 0

        outer2@ for (y in 0 until height) {
            for (x in 0 until width) {
                if (currentPixel < startPixel) {
                    currentPixel++
                    continue
                }
                if (bitIndex >= totalBitsNeeded) break@outer2

                val pixel = stegoBitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Red
                if (bitIndex < totalBitsNeeded) {
                    val bytePos = bitIndex / 8
                    val bitPos = 7 - (bitIndex % 8)
                    val bit = r and 1
                    fullPayload[bytePos] = (fullPayload[bytePos].toInt() or (bit shl bitPos)).toByte()
                    bitIndex++
                }

                // Green
                if (bitIndex < totalBitsNeeded) {
                    val bytePos = bitIndex / 8
                    val bitPos = 7 - (bitIndex % 8)
                    val bit = g and 1
                    fullPayload[bytePos] = (fullPayload[bytePos].toInt() or (bit shl bitPos)).toByte()
                    bitIndex++
                }

                // Blue
                if (bitIndex < totalBitsNeeded) {
                    val bytePos = bitIndex / 8
                    val bitPos = 7 - (bitIndex % 8)
                    val bit = b and 1
                    fullPayload[bytePos] = (fullPayload[bytePos].toInt() or (bit shl bitPos)).toByte()
                    bitIndex++
                }
            }
        }

        val result = ByteArray(len)
        System.arraycopy(fullPayload, headerSize, result, 0, len)
        return result
    }
}
