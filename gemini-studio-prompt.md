# Master Prompt: Building the Amazfit Companion App in Gemini Studio

This document contains the prompt template and protocol context to paste into **Gemini Studio (Google AI Studio)** when generating, refactoring, or extending your Android companion application.

---

## 📋 Copy-Paste Prompt for Gemini Studio

```markdown
You are an expert Android developer specializing in Kotlin 2.0+, Jetpack Compose, Coroutines/Flow, and Bluetooth Low Energy (BLE) architecture.

I am building a clean, modern, local-first companion Android application for the Amazfit Balance smartwatch (Codename "Monaco"). The watch runs Zepp OS and communicates via Bluetooth Low Energy using Huami's proprietary GATT protocol.

Here is the exact reverse-engineered protocol specification:

### 1. GATT Services & Characteristics
* Base Service: `0000fee0-0000-1000-8000-00805f9b34fb`
* Auth Service: `0000fee1-0000-1000-8000-00805f9b34fb`
* High-Throughput Sync Service: `0000fee7-0000-1000-8000-00805f9b34fb`
* Standard Heart Rate Service: `0000180d-0000-1000-8000-00805f9b34fb`
* Standard Battery Service: `0000180f-0000-1000-8000-00805f9b34fb`

* Auth Characteristic: `00000009-0000-3512-2118-0009af100700` (Properties: Write, Notify)
* Real-time Steps Characteristic: `00000007-0000-3512-2118-0009af100700` (Properties: Read, Notify)
* Battery Status Characteristic: `00000006-0000-3512-2118-0009af100700` (Properties: Read, Notify)
* Heart Rate Measurement: `00002a37-0000-1000-8000-00805f9b34fb` (Properties: Notify)
* CCCD Descriptor: `00002902-0000-1000-8000-00805f9b34fb`

### 2. Cryptographic Authentication Handshake (AES-128-ECB)
The watch enforces a mutual authentication handshake before revealing biometrics:
1. Negotiation: Request MTU 512 immediately upon connection, then discover services.
2. Notifications: Enable notifications on Auth Characteristic `0x0009` via CCCD descriptor `0x2902`.
3. Pairing Phase:
   - For a fresh pairing: Write `[0x01, 0x00] + 16-byte random key`. The watch displays a checkmark prompt. User confirms on watch. Watch emits notification `[0x10, 0x01, 0x01]`.
   - For an existing pairing: Write `[0x02, 0x00]` to request random challenge.
4. Challenge Phase: Watch emits `[0x10, 0x02, 0x01] + 16-byte random challenge`.
5. Encryption: Encrypt the 16-byte challenge using `AES/ECB/NoPadding` with the 16-byte `authKey`.
6. Verification: Write `[0x03, 0x00] + 16-byte encrypted challenge`.
7. Success: Watch responds `[0x10, 0x03, 0x01]`. Session is now unlocked!

### 3. Real-Time Telemetry Parsing
* Steps & Activity (`0x0007`): Payload contains 3 Little-Endian 32-bit integers:
  - Offset 0..3: `steps` (Int)
  - Offset 4..7: `distance` in meters (Int)
  - Offset 8..11: `calories` in kcal (Int)
* Battery (`0x0006`):
  - Offset 0: `percentage` (0..100)
  - Offset 2: `isCharging` (1 = Charging, 0 = Discharging)
* Heart Rate (`0x2A37`):
  - Offset 0: Flags (Bit 0: 0 = 8-bit BPM, 1 = 16-bit BPM)
  - Offset 1: BPM reading

### 4. Device Identification
* Target Device: Amazfit Balance
* Codename: Monaco
* Advertised Names: `Amazfit Balance-XXXX` or `Monaco-XXXX` (where XXXX is the last 4 hex characters of the MAC address)

### Task:
[INSERT YOUR SPECIFIC PROMPT HERE, e.g.:
- "Create a Jetpack Compose Dashboard showing real-time steps, battery percentage with a charging indicator, live heart rate, and an AuthState indicator banner."
- "Implement a persistent Room Database to store daily step logs and heart rate history synced from the watch."
- "Build a settings screen allowing the user to paste their 32-character hex AuthKey or initiate a fresh Bluetooth pairing session."
]
```
