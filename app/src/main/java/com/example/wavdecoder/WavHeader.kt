package com.example.wavdecoder

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

data class WavHeader(
    val chunkId: String,
    val chunkSize: Int,
    val format: String,
    val subchunk1Id: String,
    val subchunk1Size: Int,
    val audioFormat: Short,
    val numChannels: Short,
    val sampleRate: Int,
    val byteRate: Int,
    val blockAlign: Short,
    val bitsPerSample: Short,
    val subchunk2Id: String,
    val subchunk2Size: Int
) {
    companion object {
        //The header may have a different size. In our case, for all files it is a standard 44
        const val HEADER_SIZE = 44L
        private const val BYTE_ARRAY_SIZE = 4

        fun readWaveHeader(inputStream: InputStream): WavHeader {
            val buffer = ByteBuffer.allocate(HEADER_SIZE.toInt())
            inputStream.read(buffer.array(), buffer.arrayOffset(), buffer.capacity())
            val bytes = ByteArray(BYTE_ARRAY_SIZE)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            return with(buffer) {
                WavHeader(
                    chunkId = string(bytes),
                    chunkSize = int,
                    format = string(bytes),
                    subchunk1Id = string(bytes),
                    subchunk1Size = int,
                    audioFormat = short,
                    numChannels = short,
                    sampleRate = int,
                    byteRate = int,
                    blockAlign = short,
                    bitsPerSample = short,
                    subchunk2Id = string(bytes),
                    subchunk2Size = int
                )
            }
        }

        private fun ByteBuffer.string(bytes: ByteArray): String {
            get(bytes)
            return bytes.toString(StandardCharsets.US_ASCII)
        }
    }
}