package org.openbalance.data.ble

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

object AgpsBuilder {

    const val FILE_ID_GPS_ALMANAC: Byte = 0x05
    const val FILE_ID_GLONASS_ALMANAC: Byte = 0x0F
    const val FILE_ID_BEIDOU_LLE: Byte = 0x86.toByte()
    const val FILE_ID_GPS_LLE: Byte = 0x87.toByte()
    const val FILE_ID_GLONASS_LLE: Byte = 0x88.toByte()
    const val FILE_ID_GALILEO_LLE: Byte = 0x89.toByte()
    const val FILE_ID_QZSS_LLE: Byte = 0x8A.toByte()

    /**
     * Packages individual constellation almanac/ephemeris files into the proprietary UIHH binary container.
     */
    fun buildUihhPackage(filesMap: Map<Byte, ByteArray>): ByteArray {
        val bodyStream = ByteArrayOutputStream()

        for ((fileId, content) in filesMap) {
            val crc = CRC32()
            crc.update(content)
            val crcValue = crc.value.toInt()

            // 10-byte chunk header: [0x01, FileID, 4B Length LE, 4B CRC32 LE]
            val chunkHeader = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
                .put(0x01.toByte())
                .put(fileId)
                .putInt(content.size)
                .putInt(crcValue)
                .array()

            bodyStream.write(chunkHeader)
            bodyStream.write(content)
        }

        val body = bodyStream.toByteArray()

        val masterCrc = CRC32()
        masterCrc.update(body)
        val masterCrcValue = masterCrc.value.toInt()

        // Master Header: b"UIHH" + [0x04, 0, 0, 0, 0, 0, 0, 0x01] + 4B CRC + 6B 0 + 4B Len + 6B 0
        val masterHeader = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .put("UIHH".toByteArray(Charsets.US_ASCII))
            .put(byteArrayOf(0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01))
            .putInt(masterCrcValue)
            .put(ByteArray(6))
            .putInt(body.size)
            .put(ByteArray(6))
            .array()

        val finalStream = ByteArrayOutputStream()
        finalStream.write(masterHeader)
        finalStream.write(body)

        return finalStream.toByteArray()
    }
}
