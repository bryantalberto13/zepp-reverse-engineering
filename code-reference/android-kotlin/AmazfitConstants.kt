package org.openbalance.data.ble

import java.util.UUID

object AmazfitConstants {
    // -------------------------------------------------------------
    // Primary GATT Services
    // -------------------------------------------------------------
    val SERVICE_MIBAND_1: UUID = UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb")
    val SERVICE_MIBAND_2: UUID = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb") // Auth & Handshake
    val SERVICE_STREAMING: UUID = UUID.fromString("0000fee7-0000-1000-8000-00805f9b34fb") // High-throughput sync
    val SERVICE_HEART_RATE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val SERVICE_BATTERY: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val SERVICE_DEVICE_INFO: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")

    // -------------------------------------------------------------
    // Huami Custom Characteristics (Base 0000xxxx-0000-3512-2118-0009af100700)
    // -------------------------------------------------------------
    val CHAR_AUTH: UUID = UUID.fromString("00000009-0000-3512-2118-0009af100700")
    val CHAR_REALTIME_STEPS: UUID = UUID.fromString("00000007-0000-3512-2118-0009af100700")
    val CHAR_BATTERY: UUID = UUID.fromString("00000006-0000-3512-2118-0009af100700")
    val CHAR_CHUNK_DATA: UUID = UUID.fromString("00000004-0000-3512-2118-0009af100700")
    val CHAR_CHUNK_CTRL: UUID = UUID.fromString("00000005-0000-3512-2118-0009af100700")

    // -------------------------------------------------------------
    // Bluetooth Standard Characteristics & Descriptors
    // -------------------------------------------------------------
    val CHAR_HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    val CHAR_HEART_RATE_CONTROL: UUID = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb")
    val CCCD_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // -------------------------------------------------------------
    // Authentication Handshake Protocol Opcodes
    // -------------------------------------------------------------
    const val AUTH_OP_SEND_KEY: Byte = 0x01
    const val AUTH_OP_REQUEST_RANDOM: Byte = 0x02
    const val AUTH_OP_SEND_ENCRYPTED: Byte = 0x03
    const val AUTH_OP_RESPONSE: Byte = 0x10

    const val AUTH_RESULT_SUCCESS: Byte = 0x01
    const val AUTH_RESULT_FAIL: Byte = 0x04

    // -------------------------------------------------------------
    // Hardware Matchers & Discovery Filters
    // -------------------------------------------------------------
    val DEVICE_NAME_REGEX = Regex("^(Amazfit Balance|Monaco)[-_ ]?.*", RegexOption.IGNORE_CASE)

    // Device Enum Identifiers (from zhk.java)
    const val DEVICE_ID_MILI_MONACO = 8519936
    const val DEVICE_ID_MILI_MONACO_WN = 8519937
    const val DEVICE_ID_MILI_MONACO_W = 8519939
}
