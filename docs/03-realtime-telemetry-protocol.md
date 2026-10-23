# Real-time Telemetry Protocol Specification

Once the authentication handshake completes successfully, the central app can subscribe to real-time telemetry streams on Service `0x0000fee0-0000-1000-8000-00805f9b34fb`.

---

## 1. Real-time Steps, Distance & Calories

* **Characteristic UUID:** `00000007-0000-3512-2118-0009af100700`
* **Trigger:** Emitted periodically whenever steps are counted, or when requested by a GATT read.
* **Notification Payload Format:** 12 to 16 bytes, Little-Endian integers.

### Binary Layout:
```
+---------------+---------------+---------------+---------------+
| Byte 0 - 3    | Byte 4 - 7    | Byte 8 - 11   | Byte 12 - 15  |
+---------------+---------------+---------------+---------------+
| Steps Count   | Distance (m)  | Calories      | Reserved      |
| (int32_le)    | (int32_le)    | (int32_le)    | (Optional)    |
+---------------+---------------+---------------+---------------+
```

### Field Definitions:
| Field | Offset | Length | Type | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Steps** | `0` | 4 bytes | `int32` (Little Endian) | Cumulative daily step count |
| **Distance** | `4` | 4 bytes | `int32` (Little Endian) | Cumulative distance traveled today in **meters** |
| **Calories** | `8` | 4 bytes | `int32` (Little Endian) | Active calories burned today in **kcal** |

### Kotlin Parser:
```kotlin
fun parseRealtimeActivity(data: ByteArray): RealtimeActivity {
    require(data.size >= 4) { "Payload too short for steps" }
    val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    
    val steps = buffer.int
    val distance = if (data.size >= 8) buffer.int else 0
    val calories = if (data.size >= 12) buffer.int else 0
    
    return RealtimeActivity(steps, distance, calories)
}
```

---

## 2. Battery Status

* **Characteristic UUID:** `00000006-0000-3512-2118-0009af100700` (Huami custom) or standard `00002a19-0000-1000-8000-00805f9b34fb` (Battery Level)
* **Properties:** `READ`, `NOTIFY`

### Binary Layout:
```
+---------+---------+---------+-----------------------+
| Byte 0  | Byte 1  | Byte 2  | Bytes 3 - 6           |
+---------+---------+---------+-----------------------+
| Level % | State   | Charge  | Last Charge Timestamp |
| (uint8) | (uint8) | (uint8) | (uint32_le unix secs) |
+---------+---------+---------+-----------------------+
```

### Field Definitions:
* **Byte 0 (Level):** `0` to `100` (%)
* **Byte 1 (Battery Status):** Status flags (e.g. `0x01` = Normal, `0x02` = Low)
* **Byte 2 (Charging Flag):** `0x01` if currently connected to charger, `0x00` if discharging.
* **Bytes 3–6 (Timestamp):** Epoch seconds of the last full charge event.

---

## 3. Continuous Heart Rate Streaming

* **Service:** Standard Bluetooth SIG Heart Rate Service `0000180d-0000-1000-8000-00805f9b34fb`
* **Heart Rate Measurement:** `00002a37-0000-1000-8000-00805f9b34fb`
* **Heart Rate Control Point:** `00002a39-0000-1000-8000-00805f9b34fb`

### Packet Structure (Bluetooth SIG Standard):
```
+---------+-----------------------+-----------------------------+
| Byte 0  | Byte 1 (or 1 & 2)     | Optional Bytes              |
+---------+-----------------------+-----------------------------+
| Flags   | Heart Rate (BPM)      | Energy Expended / RR-Values |
+---------+-----------------------+-----------------------------+
```

* **Flags (Byte 0):**
  * `bit 0`: HR format: `0` = UINT8 (1 byte BPM), `1` = UINT16 (2 bytes BPM).
  * `bit 1 & 2`: Sensor Contact Status.
  * `bit 3`: Energy Expended field present.
  * `bit 4`: RR-Interval values present.

### Activating Real-Time HR Monitoring:
To force continuous 1-second PPG sampling, the central writes the continuous monitoring control command to `0x2A39`:
```kotlin
// Enable continuous sensor sampling
val enableContinuousCmd = byteArrayOf(0x15, 0x01, 0x01)
gatt.writeCharacteristic(hrControlChar, enableContinuousCmd)

// Disable continuous sensor sampling
val disableContinuousCmd = byteArrayOf(0x15, 0x01, 0x00)
gatt.writeCharacteristic(hrControlChar, disableContinuousCmd)
```
