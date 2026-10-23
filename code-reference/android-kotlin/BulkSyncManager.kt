package org.openbalance.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.openbalance.data.ble.model.MinuteActivityRecord
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import java.util.TimeZone

sealed class SyncState {
    data object Idle : SyncState()
    data object RequestingStartDate : SyncState()
    data class Streaming(val receivedBytes: Int, val totalExpectedBytes: Int) : SyncState()
    data class Finished(val recordCount: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

class BulkSyncManager(private val gatt: BluetoothGatt) {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val streamBuffer = ByteArrayOutputStream()
    private var expectedTotalLength = 0
    private var lastSequenceNumber = -1
    private var startTimestampMs = 0L

    private val _syncedRecords = MutableStateFlow<List<MinuteActivityRecord>>(emptyList())
    val syncedRecords: StateFlow<List<MinuteActivityRecord>> = _syncedRecords.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startHistoricalSync(sinceCalendar: Calendar) {
        val baseService = gatt.getService(AmazfitConstants.SERVICE_MIBAND_1)
        if (baseService == null) {
            _syncState.value = SyncState.Error("Base service 0xFEE0 not found")
            return
        }

        val metaChar = baseService.getCharacteristic(AmazfitConstants.CHAR_BULK_METADATA)
        val dataChar = baseService.getCharacteristic(AmazfitConstants.CHAR_BULK_DATA)

        if (metaChar == null || dataChar == null) {
            _syncState.value = SyncState.Error("Bulk characteristics 0x0004 or 0x0005 not found")
            return
        }

        streamBuffer.reset()
        lastSequenceNumber = -1
        expectedTotalLength = 0

        // Enable notifications on both endpoints
        enableNotification(metaChar)
        enableNotification(dataChar)

        // Build Start Date payload: [0x01, FetchType, Y_LSB, Y_MSB, Month, Day, Hour, Min, TZ_MSB, TZ_LSB]
        val year = sinceCalendar.get(Calendar.YEAR)
        val month = (sinceCalendar.get(Calendar.MONTH) + 1).toByte()
        val day = sinceCalendar.get(Calendar.DAY_OF_MONTH).toByte()
        val hour = sinceCalendar.get(Calendar.HOUR_OF_DAY).toByte()
        val minute = sinceCalendar.get(Calendar.MINUTE).toByte()

        val tz = TimeZone.getDefault()
        val tzUnits = (tz.getOffset(sinceCalendar.timeInMillis) / 1000 / 60 / 15).toShort()

        val payload = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            .put(AmazfitConstants.SYNC_CMD_START_DATE)
            .put(AmazfitConstants.FETCH_TYPE_ACTIVITY)
            .putShort(year.toShort())
            .put(month)
            .put(day)
            .put(hour)
            .put(minute)
            .order(ByteOrder.BIG_ENDIAN)
            .putShort(tzUnits)
            .array()

        _syncState.value = SyncState.RequestingStartDate
        writeCharacteristic(metaChar, payload)
    }

    @SuppressLint("MissingPermission")
    fun onMetadataNotification(data: ByteArray) {
        if (data.size < 3 || data[0] != AmazfitConstants.AUTH_OP_RESPONSE) return

        val cmd = data[1]
        val status = data[2]

        when (cmd) {
            AmazfitConstants.SYNC_CMD_START_DATE -> {
                if (status == AmazfitConstants.AUTH_RESULT_SUCCESS && data.size >= 7) {
                    val buf = ByteBuffer.wrap(data, 3, 4).order(ByteOrder.LITTLE_ENDIAN)
                    expectedTotalLength = buf.int
                    _syncState.value = SyncState.Streaming(0, expectedTotalLength)

                    // Step 2: Trigger data stream by writing [0x02] (FETCH_DATA)
                    val baseService = gatt.getService(AmazfitConstants.SERVICE_MIBAND_1)
                    val metaChar = baseService?.getCharacteristic(AmazfitConstants.CHAR_BULK_METADATA)
                    if (metaChar != null) {
                        writeCharacteristic(metaChar, byteArrayOf(AmazfitConstants.SYNC_CMD_FETCH_DATA))
                    }
                } else {
                    _syncState.value = SyncState.Error("Start date rejected by watch (status: $status)")
                }
            }

            AmazfitConstants.SYNC_CMD_FETCH_DATA -> {
                if (status == AmazfitConstants.AUTH_RESULT_SUCCESS) {
                    // Stream finished successfully!
                    decodeStreamBuffer()

                    // Send ACK to watch (0x09: Keep data on watch; 0x01: Clear data)
                    val baseService = gatt.getService(AmazfitConstants.SERVICE_MIBAND_1)
                    val metaChar = baseService?.getCharacteristic(AmazfitConstants.CHAR_BULK_METADATA)
                    if (metaChar != null) {
                        writeCharacteristic(
                            metaChar,
                            byteArrayOf(AmazfitConstants.SYNC_CMD_ACK, AmazfitConstants.SYNC_ACK_KEEP_DATA)
                        )
                    }
                } else {
                    _syncState.value = SyncState.Error("Bulk stream ended with error status: $status")
                }
            }
        }
    }

    fun onDataStreamNotification(data: ByteArray) {
        if (data.isEmpty()) return

        val seq = data[0].toInt() and 0xFF
        lastSequenceNumber = seq

        // Append payload (excluding 1-byte sequence counter)
        if (data.size > 1) {
            streamBuffer.write(data, 1, data.size - 1)
        }

        _syncState.value = SyncState.Streaming(streamBuffer.size(), expectedTotalLength)
    }

    private fun decodeStreamBuffer() {
        val raw = streamBuffer.toByteArray()
        val records = mutableListOf<MinuteActivityRecord>()
        var offset = 0
        var currentTs = startTimestampMs

        while (offset + 8 <= raw.size) {
            val sample = TelemetryParser.parseMinuteSample(raw, offset)
            records.add(
                MinuteActivityRecord(
                    timestamp = currentTs,
                    kind = sample.kind,
                    intensity = sample.intensity,
                    steps = sample.steps,
                    heartRate = sample.heartRate,
                    isAsleep = sample.isAsleep,
                    isDeepSleep = sample.isDeepSleep,
                    isRemSleep = sample.isRemSleep
                )
            )
            offset += 8
            currentTs += 60_000L // Increment by 1 minute per record
        }

        _syncedRecords.value = records
        _syncState.value = SyncState.Finished(records.size)
    }

    @SuppressLint("MissingPermission")
    private fun enableNotification(char: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(char, true)
        val descriptor = char.getDescriptor(AmazfitConstants.CCCD_DESCRIPTOR) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(char: BluetoothGattCharacteristic, payload: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = payload
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
    }
}
