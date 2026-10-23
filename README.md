# Zepp & Amazfit Reverse Engineering & Protocol Specification

[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20BLE-3DDC84.svg)](https://developer.android.com)
[![Device](https://img.shields.io/badge/Target-Amazfit%20Balance%20(Monaco)-0094B8.svg)](https://www.amazfit.com)
[![Status](https://img.shields.io/badge/Status-Reverse%20Engineered-success.svg)]()
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A dedicated, comprehensive reverse-engineering repository documenting the proprietary Bluetooth Low Energy (BLE) protocols, cryptographic handshakes, telemetry streams, chunk synchronization engines, and cloud endpoints of the **Zepp Android application** (`com.huami.watch.hmwatchmanager`) and **Amazfit wearables** (with focus on **Amazfit Balance / Monaco** and Zepp OS devices).

This repository is structured specifically to serve as a ground-truth protocol specification and drop-in code reference for building custom Android companion applications (e.g., in **Gemini Studio / Google AI Studio** and **Android Studio**).

---

## 📑 Table of Contents

1. [Architecture Overview](#-architecture-overview)
2. [Target Hardware & Identifiers](#-target-hardware--identifiers)
3. [Repository Structure](#-repository-structure)
4. [Protocol Documentation Map](#-protocol-documentation-map)
5. [Quick Implementation Guide for Android / Gemini Studio](#-quick-implementation-guide-for-android--gemini-studio)
6. [Testing & Verification Tools](#-testing--verification-tools)
7. [Contributing & Disclaimers](#-disclaimer)

---

## 📡 Architecture Overview

The Zepp companion architecture communicates with Amazfit smartwatches over standard Bluetooth Low Energy (BLE), utilizing Huami's proprietary 128-bit GATT profile alongside standard Bluetooth SIG profiles:

```
+----------------------------------------------------------------------+
|                     Android Companion Application                    |
|                (Kotlin + Coroutines / Jetpack Compose)               |
+----------------------------------------------------------------------+
       |                                                |
       |  BLE Central (MTU: 512)                        |  HTTPS REST
       v                                                v
+-----------------------------+        +-------------------------------+
|    Amazfit Balance Watch    |        |        Zepp / Huami Cloud     |
|         (Zepp OS)           |        |           REST APIs           |
+-----------------------------+        +-------------------------------+
| * Auth Service (0xFEE1)     |        | * account.huami.com (Login)   |
| * Base Service (0xFEE0)     |        | * api-mifit.huami.com (Keys)  |
| * Bulk Transfer (0xFEE7)    |        | * api-user.huami.com (Tokens) |
| * Standard HR (0x180D)      |        +-------------------------------+
| * Battery (0x180F)          |
+-----------------------------+
```

### Protocol Highlights
* **Cryptographic Handshake:** Mutual challenge-response authentication using **AES-128 (ECB mode, NoPadding)** over Characteristic `00000009-0000-3512-2118-0009af100700`.
* **Real-time Telemetry:** Unencrypted, asynchronous byte streams pushed via notifications for real-time steps, distance, active calories (`00000007-...`), and battery percentage (`00000006-...`).
* **Bulk Data Streaming:** Reliable, chunk-oriented protocol over Service `0xFEE7` for synchronizing historical minute-by-minute activity, sleep epochs, continuous heart rate, and GPS track logs.

---

## ⌚ Target Hardware & Identifiers

Decompiled from the Zepp device enum table (`zhk.java` and `w5l.java`):

| Property | Value / Specification |
| :--- | :--- |
| **Product Marketing Name** | Amazfit Balance |
| **Internal Hardware Codename** | `Monaco` / `MonacoW` / `MonacoWN` |
| **Zepp Device Enum IDs** | `MILI_MONACO` (`8519936`), `MILI_MONACO_WN` (`8519937`), `MILI_MONACO_W` (`8519939`) |
| **BLE Advertisement Name Pattern** | `Amazfit Balance-[A-Fa-f0-9]{4}` or `Monaco-[A-Fa-f0-9]{4}` |
| **Display & Resolution** | 1.50" Round AMOLED (480 × 480 px, 323 PPI) |
| **Operating System** | Zepp OS 3.0 / 3.5 / 4.0 |
| **Primary Base UUID** | `0000fee0-0000-1000-8000-00805f9b34fb` |
| **Custom Characteristic Base** | `0000xxxx-0000-3512-2118-0009af100700` |

---

## 📁 Repository Structure

```
zepp-reverse-engineering/
├── README.md                               # Project overview and ground-truth index
├── gemini-studio-prompt.md                 # Context & instructions prompt for Gemini Studio
├── docs/                                   # In-depth technical protocol specifications
│   ├── 01-ble-architecture.md              # GATT Services, Characteristics, Descriptors, MTU
│   ├── 02-authentication-handshake.md     # AES-128 challenge-response & pairing sequence
│   ├── 03-realtime-telemetry-protocol.md   # Step counter, distance, calorie & battery parsing
│   ├── 04-sync-and-chunk-protocol.md       # High-throughput historical activity & workout sync
│   ├── 05-device-hardware-matrix.md        # Huami/Zepp device codenames & internal IDs
│   └── 06-cloud-api-endpoints.md           # Huami Cloud REST APIs (Auth, Keys, Sync)
├── code-reference/                         # Production-ready implementation snippets
│   └── android-kotlin/                     # Clean Kotlin code ready for Android Studio
│       ├── AmazfitConstants.kt             # Protocol UUIDs, opcodes, and regex constants
│       ├── AmazfitCrypto.kt                # AES-128 challenge encryption and key generator
│       ├── BleScanner.kt                   # Low-latency BLE discovery with hardware filtering
│       ├── BleConnectionManager.kt         # Reactive GATT connection & auth state machine
│       ├── TelemetryParser.kt              # Byte-level parsers for steps, battery, and HR
│       └── models/
│           └── Models.kt                   # Data models (AuthState, RealtimeActivity, etc.)
└── tools/                                  # Local verification scripts (Python)
    ├── verify_auth_crypto.py               # AES-128 ECB challenge-response test vector validator
    ├── parse_telemetry.py                  # CLI decoder for raw hex packets
    └── ble_inspect.py                      # Bleak-based BLE service inspection utility
```

---

## 📖 Protocol Documentation Map

Read the detailed technical documents under `/docs/`:

1. [**`docs/01-ble-architecture.md`**](docs/01-ble-architecture.md): Complete catalog of primary and secondary GATT services, characteristic handles, read/write/notify access masks, and Client Characteristic Configuration Descriptors (CCCD `0x2902`).
2. [**`docs/02-authentication-handshake.md`**](docs/02-authentication-handshake.md): Step-by-step cryptographic sequence diagram showing initial pairing (`0x01`), random challenge retrieval (`0x02`), AES-128-ECB challenge transformation, and response verification (`0x03`). Includes guide on obtaining the 16-byte Auth Key without rooting.
3. [**`docs/03-realtime-telemetry-protocol.md`**](docs/03-realtime-telemetry-protocol.md): Byte layouts, little-endian binary decoding, scaling factors, and update frequencies for real-time steps, active calories, distance, battery percentage, charging state, and heart rate.
4. [**`docs/04-sync-and-chunk-protocol.md`**](docs/04-sync-and-chunk-protocol.md): Analysis of the bulk sync engine (`0xFEE7`), chunk sequencing, timestamp headers, minute-by-minute activity payloads (`ActivityJob`), sleep stages, and workout sessions.
5. [**`docs/05-device-hardware-matrix.md`**](docs/05-device-hardware-matrix.md): Exhaustive table of Huami device models, internal code numbers, hardware generation tags, and BLE advertising signatures.
6. [**`docs/06-cloud-api-endpoints.md`**](docs/06-cloud-api-endpoints.md): REST endpoints for Huami login, app token retrieval, and automated AuthKey recovery from the official cloud backend.

---

## 🤖 Quick Implementation Guide for Android / Gemini Studio

If you are developing a companion Android application in **Gemini Studio (Google AI Studio)**:

1. **Feed Context:** Attach or reference [gemini-studio-prompt.md](gemini-studio-prompt.md) along with `docs/01-ble-architecture.md` and `docs/02-authentication-handshake.md`.
2. **Import Reference Code:** Drop the files from `code-reference/android-kotlin/` directly into your Android project package (e.g. `com.yourname.companion.data.ble`).
3. **Connect & Pair:**
   ```kotlin
   val manager = BleConnectionManager(context)
   
   // If connecting with an existing 16-byte hex Auth Key:
   manager.connectAndAuthenticate(
       address = "XX:XX:XX:XX:XX:XX",
       authKeyHex = "0123456789ABCDEF0123456789ABCDEF",
       isNewPairing = false
   )
   
   // Collect state reactively
   lifecycleScope.launch {
       manager.authState.collect { state ->
           when (state) {
               is AuthState.Authenticated -> println("Paired and ready!")
               is AuthState.Failed -> println("Auth failed: ${state.error}")
               else -> println("Current status: $state")
           }
       }
   }
   ```

---

## 🛠 Testing & Verification Tools

The `tools/` directory includes Python utilities to verify protocol compatibility on desktop/Linux without needing an Android device:

```bash
# Verify AES-128 challenge encryption against known test vectors
python3 tools/verify_auth_crypto.py

# Decode sample telemetry hex packets
python3 tools/parse_telemetry.py --steps "E8030000B004000014000000"
# Output: Steps: 1,000 | Distance: 1,200 m | Calories: 20 kcal
```

---

## ⚠️ Disclaimer

This project is created strictly for educational, research, and interoperability purposes under fair use. All product names, logos, and brands (Zepp, Amazfit, Huami, Monaco) are property of their respective owners. This repository is not affiliated with, sponsored by, or endorsed by Zepp Health Corp. or Huami Inc.
