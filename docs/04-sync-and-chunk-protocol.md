# High-Throughput Bulk Data & Sync Protocol

This document explains the synchronization engine used by Zepp to extract historical logs, continuous 24/7 heart rate history, sleep stages, SpO2, stress, and GPS workout routes from Amazfit smartwatches.

The protocol runs on Service `0x0000fee7-0000-1000-8000-00805f9b34fb`.

---

## 1. Core Sync Architecture

In the decompiled Zepp codebase (`com.huami.device.core.sync.impl.job.milible.*`), data synchronization is split into dedicated, sequential job engines:

| Job Engine | Decompiled Class | Description |
| :--- | :--- | :--- |
| **ActivityJob** | `ActivityJob.java` | Minute-by-minute steps, active calories, sleep fragmentation (`kv.java`) |
| **HrJob** | `HrJob.java` | Continuous heart rate time-series (1-min / 5-min intervals) |
| **Spo2Job** | `Spo2Job.java` | Blood oxygen readings and nighttime hypoxemia logs |
| **PressureJob** | `PressureJob.java` | All-day stress / HRV index calculations |
| **SportRecordJob** | `SportRecordJob.java` | Workout sessions (running, cycling, strength) + GPS tracks |
| **FirstBeatJob** | `FirstBeatJob.java` | Training load, VO2 Max, recovery score, HRV readiness |
| **AfJob** | `AtrialFibrillationJob.java` | Heart irregularity & AFib screening alerts |

---

## 2. Chunk Control & Chunk Data Endpoints

Bulk transfers occur over two paired characteristics:

* **Control Characteristic (`0x00000005`):** `00000005-0000-3512-2118-0009af100700`
  * Commands written here define what time window and data type the watch should begin streaming.
  * Notifications received here signal batch boundaries, total payload sizes, and checksums.
* **Data Characteristic (`0x00000004`):** `00000004-0000-3512-2118-0009af100700`
  * Emits continuous stream bursts containing the raw packed records.
  * Negotiated with MTU 512 so each notification carries up to 480 bytes.

---

## 3. Sync Request Sequence (`ActivityJob`)

```
Central (App)                                Watch (Amazfit Balance)
      |                                                 |
      |-- 1. Enable Notifications on 0x0004 & 0x0005 -->|
      |                                                 |
      |-- 2. Send Start Sync Request ------------------>|
      |      Write to 0x0005:                           |
      |      [0x01, 0x01, Y_LSB, Y_MSB, M, D, H, M, S]  |
      |                                                 |
      |<-- 3. Header Response from 0x0005 --------------|
      |      [0x10, 0x01, 0x01, TotalLen(4B), CRC(2B)]  |
      |                                                 |
      |<-- 4. Fast Stream Bursts on 0x0004 -------------|
      |      Packet 0: [Seq(1B), Payload...]            |
      |      Packet 1: [Seq(1B), Payload...]            |
      |      ...                                        |
      |                                                 |
      |-- 5. ACK Batch / Confirmation to 0x0005 ------->|
      |      [0x02, 0x01, LastAckSeq]                   |
      |                                                 |
      |<-- 6. Finish Notification on 0x0005 ------------|
      |      [0x10, 0x02, 0x01] (Batch Finished)        |
```

---

## 4. Date & Timestamp Encoding in Sync Headers

Huami hardware encodes synchronization starting timestamps using a 7-byte or 8-byte calendar structure:

```
+------------+------------+-------+-------+-------+-------+-------+
| Byte 0     | Byte 1     | Byte 2| Byte 3| Byte 4| Byte 5| Byte 6|
+------------+------------+-------+-------+-------+-------+-------+
| Year (LSB) | Year (MSB) | Month | Day   | Hour  | Min   | Sec   |
+------------+------------+-------+-------+-------+-------+-------+
```

* **Year:** 16-bit integer Little-Endian (e.g. `2026` = `0xEA, 0x07`)
* **Month:** `1` to `12`
* **Day:** `1` to `31`
* **Hour:** `0` to `23`
* **Minute:** `0` to `59`
* **Second:** `0` to `59`

---

## 5. Minute-by-Minute Activity Record Structure (`kv.java`)

In `kv.java`, activity data is packed into 4-byte or 8-byte discrete slot records representing minute intervals:

### 4-Byte Minute Record Format:
```
+---------------+---------------+---------------+---------------+
| Byte 0        | Byte 1        | Byte 2        | Byte 3        |
+---------------+---------------+---------------+---------------+
| Mode / State  | Intensity     | Steps in Min  | Heart Rate    |
+---------------+---------------+---------------+---------------+
```

* **Mode / State (Byte 0):**
  * `0x00`: Slow / Idle
  * `0x01`: Walking
  * `0x02`: Running
  * `0x03`: Deep Sleep
  * `0x04`: Light Sleep
  * `0x05`: REM Sleep
  * `0x06`: Awake (during sleep period)
* **Intensity (Byte 1):** Raw accelerometer intensity / motion metric (`0` to `255`).
* **Steps in Minute (Byte 2):** Number of steps taken during this individual 60-second slice.
* **Heart Rate (Byte 3):** BPM recorded during this minute (or `0xFF` if off-wrist / undetected).
