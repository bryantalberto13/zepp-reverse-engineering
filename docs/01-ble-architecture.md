# BLE Architecture & GATT Services Specification

This document details the Bluetooth Low Energy (BLE) architecture, GATT services, characteristic UUIDs, descriptors, permissions, and MTU requirements reverse-engineered from the Zepp companion application and Amazfit Balance.

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
Where `xxxx` represents the specific Huami characteristic index (e.g., `0x0009` for Auth, `0x0007` for Realtime Activity, `0x0006` for Battery, `0x0004` for Chunk Data, `0x0005` for Chunk Control).

---

## 2. GATT Services Catalog

| Service Name | 128-bit UUID | Short | Description |
| :--- | :--- | :--- | :--- |
| **Base Service (MiBand 1)** | `0000fee0-0000-1000-8000-00805f9b34fb` | `0xFEE0` | Legacy device metadata, hardware revision, battery status, realtime steps |
| **Auth & Config (MiBand 2)**| `0000fee1-0000-1000-8000-00805f9b34fb` | `0xFEE1` | Cryptographic pairing & session unlock characteristic |
| **Streaming & Chunks**       | `0000fee7-0000-1000-8000-00805f9b34fb` | `0xFEE7` | High-throughput bulk data transfers (workouts, GPS tracks, historic activity) |
| **Heart Rate Service**      | `0000180d-0000-1000-8000-00805f9b34fb` | `0x180D` | Bluetooth standard Heart Rate Profile (continuous PPG measurement) |
| **Battery Service**         | `0000180f-0000-1000-8000-00805f9b34fb` | `0x180F` | Standard Bluetooth SIG battery percentage read |
| **Device Information**      | `0000180a-0000-1000-8000-00805f9b34fb` | `0x180A` | Manufacturer name, model number, serial number, firmware revision |

---

## 3. Key Characteristics Detail

### A. Authentication & Pairing
* **Service:** `0000fee1-0000-1000-8000-00805f9b34fb` (or fallback `0000fee0-...` on legacy firmware)
* **UUID:** `00000009-0000-3512-2118-0009af100700`
* **Properties:** `WRITE`, `NOTIFY`
* **CCCD:** `00002902-0000-1000-8000-00805f9b34fb` (Must write `0x01, 0x00` to enable notifications)
* **Description:** Handshake endpoint. Without completing authentication on this characteristic, the watch will withhold all biometric and sync data.

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
  * Byte 1: Unknown / battery status code
  * Byte 2: Charging state (`0x01` = Charging, `0x00` = Discharging / Normal)
  * Bytes 3-6: Last charge timestamp (Little-Endian unix seconds)

### D. Bulk Data Transfer (Chunk Stream)
* **Service:** `0000fee7-0000-1000-8000-00805f9b34fb`
* **Chunk Data Characteristic:** `00000004-0000-3512-2118-0009af100700`
  * **Properties:** `NOTIFY`, `INDICATE`
  * **CCCD:** Required (`0x01, 0x00`)
* **Chunk Control Characteristic:** `00000005-0000-3512-2118-0009af100700`
  * **Properties:** `WRITE`, `NOTIFY`
  * **Description:** Used to request sync start times, acknowledge packet sequence numbers, and trigger bulk export.

---

## 4. MTU (Maximum Transmission Unit) Negotiation

By default, BLE connections begin with an ATT MTU of 23 bytes (effective payload: 20 bytes).

* **Zepp Requirement:** Upon connection state becoming `STATE_CONNECTED`, the central application **MUST** request a larger MTU:
  ```kotlin
  gatt.requestMtu(512)
  ```
* **Why MTU 512 is Critical:**
  1. Chunk streaming and activity synchronization require packet bursts of up to 240–480 bytes per notification.
  2. With default MTU (23), sync throughput is constrained to ~1 KB/sec. With MTU 512, throughput increases to 25–40 KB/sec, enabling fast workout track transfer.
* **Order of Execution in Android:**
  1. Connect (`device.connectGatt(..., false, callback, TRANSPORT_LE)`)
  2. In `onConnectionStateChange`, when `newState == BluetoothProfile.STATE_CONNECTED`, call `gatt.requestMtu(512)`.
  3. Wait for `onMtuChanged(gatt, mtu, status)` before calling `gatt.discoverServices()`.
