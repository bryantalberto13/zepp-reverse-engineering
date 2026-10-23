# BLE Architecture & GATT Services Specification

This document details the Bluetooth Low Energy (BLE) architecture, GATT services, characteristic UUIDs, descriptors, permissions, and MTU requirements reverse-engineered from the Zepp companion application and cross-referenced with active open-source implementations (`amazfit_pyclient`).

---

## 1. Base UUIDs

Zepp devices utilize two main base UUID formats:

### Standard Bluetooth SIG Base UUID
```
0000xxxx-0000-1000-8000-00805f9b34fb
```
Where `xxxx` represents 16-bit short service or characteristic identifiers (e.g., `0xFEE0`, `0xFEE1`, `0x180D`).

### Huami Proprietary Characteristic 128-bit Base UUID
```
0000xxxx-0000-3512-2118-0009af100700
```
Where `xxxx` represents the specific Huami characteristic index (e.g., `0x0009` for Auth, `0x0007` for Realtime Activity, `0x0006` for Battery, `0x0004` for Bulk Metadata, `0x0005` for Bulk Stream, `0x0016` for Chunked TX, `0x0017` for Chunked RX).

---

## 2. GATT Services Catalog

| Service Name | 128-bit UUID | Short | Description |
| :--- | :--- | :--- | :--- |
| **Base Service (MiBand 1)** | `0000fee0-0000-1000-8000-00805f9b34fb` | `0xFEE0` | Legacy device metadata, hardware revision, battery status, realtime steps, and Modern Chunked Multiplex endpoints (`0x0016`/`0x0017`) |
| **Auth & Config (MiBand 2)**| `0000fee1-0000-1000-8000-00805f9b34fb` | `0xFEE1` | Legacy cryptographic pairing & session unlock characteristic (`0x0009`) |
| **Streaming & Chunks**       | `0000fee7-0000-1000-8000-00805f9b34fb` | `0xFEE7` | High-throughput bulk data transfers (historical activity, sleep, workouts, GPS tracks) |
| **Heart Rate Service**      | `0000180d-0000-1000-8000-00805f9b34fb` | `0x180D` | Bluetooth standard Heart Rate Profile (continuous PPG measurement) |
| **Battery Service**         | `0000180f-0000-1000-8000-00805f9b34fb` | `0x180F` | Standard Bluetooth SIG battery percentage read |
| **Device Information**      | `0000180a-0000-1000-8000-00805f9b34fb` | `0x180A` | Manufacturer name, model number, serial number, firmware revision |

---

## 3. Key Characteristics Catalog

### A. Authentication & Pairing
* **Service:** `0000fee1-0000-1000-8000-00805f9b34fb` (or fallback `0000fee0-...` on legacy firmware)
* **UUID:** `00000009-0000-3512-2118-0009af100700`
* **Properties:** `WRITE`, `NOTIFY`
* **CCCD:** `00002902-0000-1000-8000-00805f9b34fb` (Must write `0x01, 0x00` to enable notifications)
* **Description:** Legacy handshake endpoint. Uses AES-128-ECB challenge-response.

### B. Real-time Activity (Steps, Distance, Calories)
* **Service:** `0000fee0-0000-1000-8000-00805f9b34fb`
* **UUID:** `00000007-0000-3512-2118-0009af100700`
* **Properties:** `READ`, `NOTIFY`
* **CCCD:** Required for asynchronous streaming (`0x01, 0x00`).
* **Format:** 12 to 16 bytes payload containing three 32-bit Little-Endian integers (`int32` steps, `int32` distance in meters, `int32` calories).

### C. Battery Information
* **Service:** `0000fee0-0000-1000-8000-00805f9b34fb`
* **UUID:** `00000006-0000-3512-2118-0009af100700`
* **Properties:** `READ`, `NOTIFY`
* **Format:**
  * Byte 0: Battery percentage (`0` to `100`)
  * Byte 1: Status flags
  * Byte 2: Charging state (`0x01` = Charging, `0x00` = Discharging / Normal)
  * Bytes 3-6: Last charge timestamp (Little-Endian unix seconds)

### D. Bulk Data Transfer Endpoints (Historical Activity & Biometrics)
* **Service:** `0000fee0-0000-1000-8000-00805f9b34fb` / `0000fee7-...`
* **Metadata & Control Characteristic:** `00000004-0000-3512-2118-0009af100700`
  * **Properties:** `WRITE`, `NOTIFY`
  * **Description:** Initiates fetch commands, defines start timestamps and fetch types, receives total byte counts, and sends transfer ACKs.
* **Bulk Data Stream Characteristic:** `00000005-0000-3512-2118-0009af100700`
  * **Properties:** `NOTIFY`
  * **Description:** Emits continuous stream bursts with a 1-byte packet sequence counter prefix followed by raw 8-byte minute samples.

### E. Modern Chunked Multiplexing Transport (Zepp OS 2.x / 3.x / 4.x)
* **Service:** `0000fee0-0000-1000-8000-00805f9b34fb`
* **Chunked Write (TX):** `00000016-0000-3512-2118-0009af100700`
  * **Properties:** `WRITE`, `WRITE_NO_RESPONSE`
  * **Description:** Outgoing chunked packets (multiplexes Auth, Weather, Steps, Heart Rate, JS Mini-apps, HTTP bridges).
* **Chunked Notify (RX):** `00000017-0000-3512-2118-0009af100700`
  * **Properties:** `NOTIFY`
  * **CCCD:** Required (`0x01, 0x00`)
  * **Description:** Incoming chunked packets reassembled by the central app and decrypted via dynamic session keys.

---

## 4. MTU Negotiation Sequence

1. Connect (`device.connectGatt(context, false, callback, TRANSPORT_LE)`)
2. In `onConnectionStateChange`, when `newState == BluetoothProfile.STATE_CONNECTED`:
   ```kotlin
   gatt.requestMtu(512)
   ```
3. In `onMtuChanged(gatt, mtu, status)`:
   * Record negotiated MTU (typically 512 on Android 8+).
   * Call `gatt.discoverServices()`.
