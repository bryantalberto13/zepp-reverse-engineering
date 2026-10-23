# High-Throughput Bulk Data & Sync Protocol

This document details the bulk data transfer protocol used by Amazfit and Zepp OS smartwatches to export historical minute-by-minute activity, continuous heart rate, sleep metrics, blood oxygen (SpO2), stress, temperature, and sports workout sessions.

Bulk transfers operate over paired characteristics on Service `0x0000fee0-0000-1000-8000-00805f9b34fb`:
* **Metadata & Control:** `00000004-0000-3512-2118-0009af100700`
* **Data Stream:** `00000005-0000-3512-2118-0009af100700`

---

## 1. Supported Fetch Types (`FetchType`)

The first byte written in the start command defines the exact telemetry type being requested:

| Opcode / Type | Name | Description | Sample Record Size |
| :--- | :--- | :--- | :--- |
| `0x01` | `ACTIVITY` | Minute-by-minute steps, intensity, sleep stages, HR | 8 bytes / min |
| `0x02` | `MANUAL_HEART_RATE`| On-demand manual heart rate spot checks | Variable |
| `0x07` | `DEBUG_LOGS` | Watch system logs and diagnostic traces | Binary text stream |
| `0x0D` | `PAI` | Personal Activity Intelligence score logs | 4 bytes |
| `0x12` | `STRESS_MANUAL` | Manual single stress test measurements | 4 bytes |
| `0x13` | `STRESS_AUTOMATIC` | 24/7 background stress monitoring | 4 bytes / 5-min |
| `0x25` | `SPO2_NORMAL` | Daytime spot-check blood oxygen readings | 4 bytes |
| `0x26` | `SPO2_SLEEP` | Nighttime continuous sleep breathing / SpO2 logs | 4 bytes / min |
| `0x2C` | `STATISTICS` | Daily rolled-up activity summaries | Aggregated block |
| `0x2E` | `TEMPERATURE` | Skin / ambient temperature readings | 4 bytes |
| `0x38` | `SPORTS_SUMMARIES` | High-level workout history headers | Session headers |
| `0x3A` | `RESTING_HR` | Daily calculated resting heart rate | 2 bytes / day |
| `0x3D` | `SPORTS_DETAILS` | High-precision workout GPS tracks & metrics | Protobuf / Binary |

---

## 2. End-to-End Transfer Sequence

```
Central (App)                                      Watch (Amazfit Balance)
      |                                                       |
      |-- 1. Enable Notifications on 0x0004 & 0x0005 -------->|
      |                                                       |
      |-- 2. Send Start Date Request to 0x0004 -------------->|
      |      Write: [0x01, FetchType, Y_LSB, Y_MSB,           |
      |              Month, Day, Hour, Min, TZ_LSB, TZ_MSB]   |
      |                                                       |
      |<-- 3. Notification on 0x0004 (Start Response) --------|
      |      [0x10, 0x01, Status(0x01), Length(4B LE),        |
      |       StartTimestamp(8B), Unknown(1B)]                |
      |                                                       |
      |-- 4. Trigger Stream Write to 0x0004 ----------------->|
      |      Write: [0x02] (FETCH_DATA)                       |
      |                                                       |
      |<-- 5. Fast Streaming Bursts on 0x0005 ----------------|
      |      Packet 0:   [Seq(1B = 0),   Payload (MTU-3 bytes)]
      |      Packet 1:   [Seq(1B = 1),   Payload (MTU-3 bytes)]
      |      Packet 255: [Seq(1B = 255), Payload (MTU-3 bytes)]
      |      Packet 256: [Seq(1B = 0),   Payload (MTU-3 bytes)] (wraps at 256)
      |                                                       |
      |<-- 6. Notification on 0x0004 (Stream Completed) ------|
      |      [0x10, 0x02, Status(0x01)]                       |
      |                                                       |
      |-- 7. Send Transfer ACK to 0x0004 -------------------->|
      |      [0x03, ACK_BYTE]                                 |
      |      (ACK_BYTE = 0x09: Keep data on watch)            |
      |      (ACK_BYTE = 0x01: Clear/Purge data from watch)   |
```

---

## 3. Timestamp & Timezone Encoding

The start timestamp format written in Step 2:
```
+------------+------------+-------+-------+-------+-------+------------+------------+
| Byte 0     | Byte 1     | Byte 2| Byte 3| Byte 4| Byte 5| Byte 6     | Byte 7     |
+------------+------------+-------+-------+-------+-------+------------+------------+
| Year (LSB) | Year (MSB) | Month | Day   | Hour  | Min   | TZ (MSB)   | TZ (LSB)   |
+------------+------------+-------+-------+-------+-------+------------+------------+
```

* **Year:** Little-Endian uint16 (e.g. `2026` = `0xEA, 0x07`).
* **Month:** `1` to `12`.
* **Day:** `1` to `31`.
* **Hour:** `0` to `23`.
* **Minute:** `0` to `59`.
* **Timezone Offset (Bytes 6 & 7):** Big-Endian 16-bit integer representing timezone offset in **15-minute increments**:
  ```python
  tz_units = int(tz_offset_seconds // (60 * 15))
  tz_bytes = tz_units.to_bytes(2, byteorder="big", signed=True)
  ```
  *(Example: UTC-4 is `-240` minutes = `-16` units = `0xFFF0`)*.

---

## 4. 8-Byte Minute Activity Sample Format (`ActivitySample`)

For `FetchType.ACTIVITY` (`0x01`), every minute of the day is represented by an exact **8-byte binary record**:

```
+---------+-----------+---------+------------+---------+---------+------------+-----------+
| Byte 0  | Byte 1    | Byte 2  | Byte 3     | Byte 4  | Byte 5  | Byte 6     | Byte 7    |
+---------+-----------+---------+------------+---------+---------+------------+-----------+
| Kind    | Intensity | Steps   | Heart Rate | Retain  | Sleep   | Deep Sleep | REM Sleep |
+---------+-----------+---------+------------+---------+---------+------------+-----------+
```

### Field Definitions:
1. **`Kind` (uint8):** Movement category classification:
   * `0x00`: Sedentary / Rest
   * `0x01`: Walking
   * `0x02`: Running
   * `0x03` - `0x07`: Sleep states
2. **`Intensity` (uint8):** Accelerometer intensity (`0` to `255`).
3. **`Steps` (uint8):** Discrete step count accumulated inside this specific 60-second slice.
4. **`Heart Rate` (uint8):** Heart rate BPM recorded during this minute (or `0xFF` if not detected).
5. **`Retain` (uint8):** Internal metric / flags.
6. **`Sleep` (uint8):** General sleep state flag (`1` = asleep, `0` = awake).
7. **`Deep Sleep` (uint8):** `1` if minute is classified as Deep / Slow-wave Sleep (N3).
8. **`REM Sleep` (uint8):** `1` if minute is classified as REM (Rapid Eye Movement).
