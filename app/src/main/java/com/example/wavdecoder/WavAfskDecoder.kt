package com.example.wavdecoder

import java.io.InputStream
import java.util.BitSet

class WavAfskDecoder {
    private val id = ArrayList<Byte>(STREAM_ID_SIZE)
    private val messageStringBuilder = StringBuilder()
    private val currentBitSet = BitSet()
    private val toneData = mutableListOf<Byte>() // fill by 31 bytes
    private var isDataReadingInProgress = false
    private var toneDataCount = 0 // should be 1984

    companion object {
        private const val SHIFTING_VALUE = 8
        private const val FF_VALUE = 0xFF

        // Encoding byte-stream
        private const val ALLOWABLE_SIGNAL_LENGTH_ERROR = 1 // Real-life data might not be an ideal rectangle
        private const val ONE_SIGNAL_TIME_SEC = 0.000320
        private const val STREAM_ID_SIZE = 2
        private const val STREAM_ID_FIRST_BYTE = 0x42
        private const val STREAM_ID_SECOND_BYTE = 0x03
        private const val TONE_DATA_SIZE = 30
        private const val TONE_DATA_COUNT = 1984 // Is this a reference to Orwell? ◕‿◕
        private const val ONE_BYTE_DATA_SIZE = 11
        private const val MODULE_VALUE = 256

        // Encoding bit-stream
        private const val START_BIT_BOOLEAN_VALUE = false
        private const val START_BIT_POSITION = 0
        private const val FIRST_STOP_BIT_BOOLEAN_VALUE = true
        private const val FIRST_STOP_BIT_POSITION = 9
        private const val SECOND_STOP_BIT_BOOLEAN_VALUE = true
        private const val SECOND_STOP_BIT_POSITION = 10
        private const val DATA_START_BIT_POSITION = 1
        private const val DATA_END_BIT_POSITION = 8
    }

    fun getDecodedMessage(byteStream: InputStream): String {
        byteStream.use {
            val waveHeader = WavHeader.readWaveHeader(byteStream.apply { mark(0) })
            byteStream.reset()
            // use a subchunk2size to create the exact size
            val byteArray = ByteArray(waveHeader.subchunk2Size)
            val monoChannelArray = ShortArray(byteArray.size / 2)
            // skip the Wave-header
            byteStream.skip(WavHeader.HEADER_SIZE)
            return if (byteStream.read(byteArray) != -1) {
                // convert all data from 2 channels to mono channel
                for (i in 0 until waveHeader.subchunk2Size step 2) {
                    monoChannelArray[i / 2] = ((byteArray[i + 1].toInt() shl SHIFTING_VALUE)
                            or (byteArray[i].toInt() and FF_VALUE)).toShort()
                }
                val monoAvgByteArray = convertToAvgMonoChannel(monoChannelArray)
                decodeSignal(monoAvgByteArray, getSignalsLengths(waveHeader.sampleRate))
            } else {
                "(ノಠ益ಠ)ノ彡┻━┻ Something goes wrong..."
            }
        }
    }

    private fun getSignalsLengths(sampleRate: Int): Pair<Int, Int> {
        val minOneSignalLength = sampleRate * ONE_SIGNAL_TIME_SEC - ALLOWABLE_SIGNAL_LENGTH_ERROR
        val minZeroSignalLength = sampleRate * ONE_SIGNAL_TIME_SEC * 2 - ALLOWABLE_SIGNAL_LENGTH_ERROR
        return Pair(minOneSignalLength.toInt(), minZeroSignalLength.toInt())
    }

    private fun decodeSignal(monoByteArray: ShortArray, signalsLengths: Pair<Int, Int>): String {
        initState()
        val minOneSignalLength = signalsLengths.first
        val minZeroSignalLength = signalsLengths.second

        var prevSignalState: SignalState = SignalState.NULL
        var currentSignalLength = 0
        var currentReadBitsLength = 0

        var i = 0
        while (i < monoByteArray.size - 1) {
            val currentValue = monoByteArray[i]
            val currentSignalState = getSignalState(currentValue)
            when (prevSignalState) {
                SignalState.NULL -> {
                    prevSignalState = currentSignalState
                    i++
                    continue
                }
                currentSignalState -> {
                    currentSignalLength++
                    i++
                }
                else -> {
                    if (currentSignalLength >= minZeroSignalLength) {
                        currentBitSet.set(currentReadBitsLength++, false)
                    } else if (currentSignalLength >= minOneSignalLength) {
                        currentBitSet.set(currentReadBitsLength++, true)
                    }
                    currentSignalLength = 0
                }
            }

            if (currentReadBitsLength < ONE_BYTE_DATA_SIZE) {
                prevSignalState = currentSignalState
                continue
            }

            val decodedByte = decodeByteOfInfo(currentBitSet)
            if (decodedByte == null) {
                currentReadBitsLength = 0
                currentBitSet.clear()
                i++
                continue
            }
            decodeToneData(decodedByte)
            currentReadBitsLength = 0
            currentBitSet.clear()
        }
        val message = messageStringBuilder.toString()
        println("(ﾉ◕ヮ◕)ﾉ* Decoded message: $message")
        return message
    }

    private fun initState() {
        messageStringBuilder.clear()
        currentBitSet.clear()
        isDataReadingInProgress = false
        toneData.clear()
    }

    private fun decodeToneData(
        decodedByte: Byte,
    ) {
        if (isDataReadingInProgress) {
            if (toneData.size < TONE_DATA_SIZE) {
                toneData.add(decodedByte)
            } else {
                checksumIsCorrect(toneData, decodedByte.toInt())
                val chars = toneData.map {
                    it.toInt()
                        .toChar()
                }
                messageStringBuilder.append(
                    chars.joinToString("")
                )
                toneDataCount += TONE_DATA_SIZE + 1 //plus checksum byte
                toneData.clear()
            }

            if (toneDataCount >= TONE_DATA_COUNT) {
                isDataReadingInProgress = false
            }
        } else {
            // check is it ID's byte
            if (decodedByte.toInt() == STREAM_ID_FIRST_BYTE) {
                id.add(decodedByte)
            } else if (decodedByte.toInt() == STREAM_ID_SECOND_BYTE && id[0].toInt() == STREAM_ID_FIRST_BYTE) {
                id.add(decodedByte)
                isDataReadingInProgress = true
            }
        }
    }

    private fun getSignalState(currentValue: Short) =
        when {
            currentValue > 0 -> SignalState.HIGHER_WHEN_NULL
            currentValue < 0 -> SignalState.LOWER_WHEN_NULL
            else -> SignalState.NULL
        }

    private fun checksumIsCorrect(toneData: MutableList<Byte>, controlChecksum: Int) {
        val toneDataChecksum = toneData.sum() % MODULE_VALUE
        if (toneDataChecksum == controlChecksum) {
            println("(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧ Checksum is correct! Checksum: $toneDataChecksum, control checksum: $controlChecksum")
        } else {
            // Not all bytes of data have a checksum equal to the checksum from the 31st byte. Unfortunately, I did not have enough time to figure out exactly what the problem is :(
            println("(ノಠ益ಠ)ノ彡┻━┻ Ups...checksum is wrong. Checksum: $toneDataChecksum, control checksum: $controlChecksum")
        }
    }

    private fun decodeByteOfInfo(currentBitSet: BitSet): Byte? {
        return if (currentBitSet[START_BIT_POSITION] == START_BIT_BOOLEAN_VALUE
            && currentBitSet[FIRST_STOP_BIT_POSITION] == FIRST_STOP_BIT_BOOLEAN_VALUE
            && currentBitSet[SECOND_STOP_BIT_POSITION] == SECOND_STOP_BIT_BOOLEAN_VALUE
        ) {
            currentBitSet.get(DATA_START_BIT_POSITION, DATA_END_BIT_POSITION)
                .toByteArray()
                .takeIf { it.isNotEmpty() }
                ?.get(0)
        } else null

    }

    private fun convertToAvgMonoChannel(data: ShortArray): ShortArray {
        val avgMonoChannel = ShortArray(data.size / 2)
        for (i in data.indices step 2) {
            val avg = (data[i] + data[i + 1]) / 2
            avgMonoChannel[i / 2] = avg.toShort()
        }
        return avgMonoChannel
    }

    enum class SignalState {
        HIGHER_WHEN_NULL,
        LOWER_WHEN_NULL,
        NULL
    }
}