package org.openbalance.data.ble

import org.openbalance.data.ble.model.BatteryInfo
import org.openbalance.data.ble.model.HeartRateReading
import org.openbalance.data.ble.model.RealtimeActivity
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ActivityMinuteSample(
    val kind: Int,
    val intensity: Int,
    val steps: Int,
    val heartRate: Int,
    val isAsleep: Boolean,
    val isDeepSleep: Boolean,
    val isRemSleep: Boolean
)

object TelemetryParser {

    /**
     * Parses real-time activity notifications from Characteristic 00000007-0000-3512-2118-0009af100700.
     * Contains 3 Little-Endian 32-bit integers: Steps, Distance (meters), Calories (kcal).
     */
    fun parseRealtimeActivity(data: ByteArray): RealtimeActivity {
        if (data.size < 4) return RealtimeActivity()

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val steps = buffer.int
        val distance = if (data.size >= 8) buffer.int else 0
        val calories = if (data.size >= 12) buffer.int else 0

        return RealtimeActivity(
            steps = steps,
            distanceMeters = distance,
            calories = calories
        )
    }

    /**
     * Parses an 8-byte minute activity sample from Bulk Sync (FetchType.ACTIVITY 0x01).
     * Structure: [kind, intensity, steps, heart_rate, retain, sleep, deep_sleep, rem_sleep]
     */
    fun parseMinuteSample(data: ByteArray, offset: Int = 0): ActivityMinuteSample {
        require(data.size - offset >= 8) { "Data buffer must contain at least 8 bytes from offset" }

        val kind = data[offset].toInt() and 0xFF
        val intensity = data[offset + 1].toInt() and 0xFF
        val steps = data[offset + 2].toInt() and 0xFF
        val hr = data[offset + 3].toInt() and 0xFF
        // offset + 4 is retain/flags
        val sleep = (data[offset + 5].toInt() and 0xFF) == 1
        val deepSleep = (data[offset + 6].toInt() and 0xFF) == 1
        val remSleep = (data[offset + 7].toInt() and 0xFF) == 1

        return ActivityMinuteSample(
            kind = kind,
            intensity = intensity,
            steps = steps,
            heartRate = if (hr == 0xFF) 0 else hr,
            isAsleep = sleep,
            isDeepSleep = deepSleep,
            isRemSleep = remSleep
        )
    }

    /**
     * Parses battery information from Characteristic 00000006-0000-3512-2118-0009af100700.
     * Format: Byte 0 = level (0-100), Byte 2 = charging flag.
     */
    fun parseBattery(data: ByteArray): BatteryInfo {
        if (data.isEmpty()) return BatteryInfo()

        val level = data[0].toInt() and 0xFF
        val isCharging = if (data.size > 2) data[2].toInt() == 1 else false
        val lastCharged = if (data.size >= 7) {
            val buf = ByteBuffer.wrap(data, 3, 4).order(ByteOrder.LITTLE_ENDIAN)
            buf.int.toLong() and 0xFFFFFFFFL
        } else {
            0L
        }

        return BatteryInfo(
            percentage = level,
            isCharging = isCharging,
            lastChargedTimestamp = lastCharged
        )
    }

    /**
     * Parses standard Bluetooth SIG Heart Rate Measurement (UUID: 00002a37-0000-1000-8000-00805f9b34fb).
     */
    fun parseHeartRate(data: ByteArray): HeartRateReading {
        if (data.isEmpty()) return HeartRateReading()

        val flags = data[0].toInt() and 0xFF
        val is16Bit = (flags and 0x01) != 0
        val contactStatus = (flags shr 1) and 0x03
        val isContactDetected = contactStatus == 3 || contactStatus == 2

        val bpm = if (is16Bit && data.size >= 3) {
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        } else if (data.size >= 2) {
            data[1].toInt() and 0xFF
        } else {
            0
        }

        return HeartRateReading(
            bpm = bpm,
            isContactDetected = isContactDetected,
            timestamp = System.currentTimeMillis()
        )
    }
}
