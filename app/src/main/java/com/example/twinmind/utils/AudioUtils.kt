package com.example.twinmind.utils

import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

object AudioUtils {
    fun shortsToBytes(shorts: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            bytes[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((shorts[i].toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    fun calculateRms(buffer: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            sum += buffer[i].toDouble() * buffer[i].toDouble()
        }
        return sqrt(sum / count)
    }

    fun writeWavFile(file: File, pcmData: ByteArray) {
        val sampleRate = Constants.SAMPLE_RATE
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize

        FileOutputStream(file).use { out ->
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            out.write(intToLittleEndian(totalSize))
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            out.write(intToLittleEndian(16))
            out.write(shortToLittleEndian(1.toShort()))
            out.write(shortToLittleEndian(channels.toShort()))
            out.write(intToLittleEndian(sampleRate))
            out.write(intToLittleEndian(byteRate))
            out.write(shortToLittleEndian(blockAlign.toShort()))
            out.write(shortToLittleEndian(bitsPerSample.toShort()))
            out.write("data".toByteArray(Charsets.US_ASCII))
            out.write(intToLittleEndian(dataSize))
            out.write(pcmData)
        }
    }

    private fun intToLittleEndian(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private fun shortToLittleEndian(value: Short): ByteArray = byteArrayOf(
        (value.toInt() and 0xFF).toByte(),
        ((value.toInt() shr 8) and 0xFF).toByte()
    )
}
