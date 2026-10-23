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

    // Bulk Historical Transfer Endpoints
    val CHAR_BULK_METADATA: UUID = UUID.fromString("00000004-0000-3512-2118-0009af100700") // Write command & start dates
    val CHAR_BULK_DATA: UUID = UUID.fromString("00000005-0000-3512-2118-0009af100700")     // Stream bursts (1B seq + data)

    // Modern Zepp OS Chunked Multiplexed Transport Endpoints
    val CHAR_CHUNKED_TX: UUID = UUID.fromString("00000016-0000-3512-2118-0009af100700")   // App -> Watch (Write)
    val CHAR_CHUNKED_RX: UUID = UUID.fromString("00000017-0000-3512-2118-0009af100700")   // Watch -> App (Notify)

    // -------------------------------------------------------------
    // Bluetooth Standard Characteristics & Descriptors
    // -------------------------------------------------------------
    val CHAR_HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    val CHAR_HEART_RATE_CONTROL: UUID = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb")
    val CCCD_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // -------------------------------------------------------------
    // Legacy Authentication Handshake Protocol Opcodes (0x0009)
    // -------------------------------------------------------------
    const val AUTH_OP_SEND_KEY: Byte = 0x01
    const val AUTH_OP_REQUEST_RANDOM: Byte = 0x02
    const val AUTH_OP_SEND_ENCRYPTED: Byte = 0x03
    const val AUTH_OP_RESPONSE: Byte = 0x10

    const val AUTH_RESULT_SUCCESS: Byte = 0x01
    const val AUTH_RESULT_FAIL: Byte = 0x04

    // -------------------------------------------------------------
    // Bulk Sync Commands (0x0004)
    // -------------------------------------------------------------
    const val SYNC_CMD_START_DATE: Byte = 0x01
    const val SYNC_CMD_FETCH_DATA: Byte = 0x02
    const val SYNC_CMD_ACK: Byte = 0x03

    const val SYNC_ACK_KEEP_DATA: Byte = 0x09 // Non-destructive read
    const val SYNC_ACK_PURGE_DATA: Byte = 0x01 // Clear synced data on watch

    // -------------------------------------------------------------
    // Bulk Fetch Types
    // -------------------------------------------------------------
    const val FETCH_TYPE_ACTIVITY: Byte = 0x01
    const val FETCH_TYPE_MANUAL_HR: Byte = 0x02
    const val FETCH_TYPE_DEBUG_LOGS: Byte = 0x07
    const val FETCH_TYPE_PAI: Byte = 0x0D
    const val FETCH_TYPE_STRESS_MANUAL: Byte = 0x12
    const val FETCH_TYPE_STRESS_AUTOMATIC: Byte = 0x13
    const val FETCH_TYPE_SPO2_NORMAL: Byte = 0x25
    const val FETCH_TYPE_SPO2_SLEEP: Byte = 0x26
    const val FETCH_TYPE_STATISTICS: Byte = 0x2C
    const val FETCH_TYPE_TEMPERATURE: Byte = 0x2E
    const val FETCH_TYPE_SPORTS_SUMMARIES: Byte = 0x38
    const val FETCH_TYPE_RESTING_HR: Byte = 0x3A
    const val FETCH_TYPE_SPORTS_DETAILS: Byte = 0x3D

    // -------------------------------------------------------------
    // Modern Chunked Endpoints (0x0016 / 0x0017)
    // -------------------------------------------------------------
    const val ENDPOINT_HTTP = 0x0001
    const val ENDPOINT_WIFI = 0x0003
    const val ENDPOINT_CALENDAR = 0x0007
    const val ENDPOINT_CONFIG = 0x000A
    const val ENDPOINT_WEATHER = 0x000E
    const val ENDPOINT_ALARMS = 0x000F
    const val ENDPOINT_CONNECT = 0x0015
    const val ENDPOINT_STEPS = 0x0016
    const val ENDPOINT_WORKOUT = 0x0019
    const val ENDPOINT_MUSIC = 0x001B
    const val ENDPOINT_HEARTRATE = 0x001D
    const val ENDPOINT_NOTIFICATIONS = 0x001E
    const val ENDPOINT_BATTERY = 0x0029
    const val ENDPOINT_SLEEP_DATA = 0x0032
    const val ENDPOINT_AUTH_ECDH = 0x0082
    const val ENDPOINT_JS_MINIAPP = 0x00A0

    // -------------------------------------------------------------
    // Hardware Matchers & Discovery Filters
    // -------------------------------------------------------------
    val DEVICE_NAME_REGEX = Regex("^(Amazfit Balance|Monaco)[-_ ]?.*", RegexOption.IGNORE_CASE)

    // Device Enum Identifiers (from zhk.java)
    const val DEVICE_ID_MILI_MONACO = 8519936
    const val DEVICE_ID_MILI_MONACO_WN = 8519937
    const val DEVICE_ID_MILI_MONACO_W = 8519939
}
