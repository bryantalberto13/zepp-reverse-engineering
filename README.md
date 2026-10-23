# Zepp & Amazfit Reverse Engineering & Protocol Specification

[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20BLE-3DDC84.svg)](https://developer.android.com)
[![Device](https://img.shields.io/badge/Target-Amazfit%20Balance%20(Monaco)-0094B8.svg)](https://www.amazfit.com)
[![Status](https://img.shields.io/badge/Status-Reverse%20Engineered-success.svg)]()
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A dedicated, comprehensive reverse-engineering repository documenting the proprietary Bluetooth Low Energy (BLE) protocols, cryptographic handshakes, telemetry streams, chunk synchronization engines, and cloud endpoints of the **Zepp Android application** (`com.huami.watch.hmwatchmanager`) and **Amazfit wearables** (with focus on **Amazfit Balance / Monaco** and Zepp OS devices).

Cross-referenced against official APK decompilations and active open-source client implementations (`MyrikLD/amazfit_pyclient`).

---

## 📑 Table of Contents

1. [Architecture Overview: Dual-Mode Stack](#-architecture-overview-dual-mode-stack)
2. [Target Hardware & Identifiers](#-target-hardware--identifiers)
3. [Repository Structure](#-repository-structure)
4. [Protocol Documentation Map](#-protocol-documentation-map)
5. [Quick Implementation Guide for Android / Gemini Studio](#-quick-implementation-guide-for-android--gemini-studio)
6. [Testing & Verification Tools](#-testing--verification-tools)
7. [Contributing & Disclaimers](#-disclaimer)

---

## 📡 Architecture Overview: Dual-Mode Stack

Modern Amazfit smartwatches (Amazfit Balance, GTR 4, GTS 4, Cheetah, T-Rex Ultra) running **Zepp OS** expose a **Dual-Mode Stack**:

```
+----------------------------------------------------------------------+
|                     Android Companion Application                    |
|                (Kotlin + Coroutines / Jetpack Compose)               |
+----------------------------------------------------------------------+
       |                                                |
       |  BLE Central (MTU: 512)                        |  HTTPS REST
       v                                                v
+-------------------------------------------------+   +-------------------------------+
|              Amazfit Balance Watch              |   |        Zepp / Huami Cloud     |
|                   (Zepp OS)                     |   |           REST APIs           |
+-------------------------------------------------+   +-------------------------------+
| [A] Modern Chunked Multiplexed Transport        |   | * account.huami.com (Login)   |
|     * TX: 0x0016 / RX: 0x0017                   |   | * api-mifit.huami.com (Keys)  |
|     * ECDH Handshake (SECT163R2) -> AES Session |   | * api-user.huami.com (Tokens) |
|     * Endpoints: STEPS, WORKOUT, WEATHER, JS... |   +-------------------------------+
|                                                 |
| [B] Direct Telemetry & Bulk Sync Layer          |
|     * Auth Handshake (0x0009 - AES-128-ECB)     |
|     * Realtime Activity (0x0007 - Steps/Cal)    |
|     * Battery Status (0x0006 - Level/Charge)    |
|     * Bulk Metadata (0x0004 - Start/Ack)        |
|     * Bulk Data Stream (0x0005 - 8B Min Samples)|
|     * Standard PPG Heart Rate (0x180D / 0x2A37) |
+-------------------------------------------------+
```

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
│   ├── 04-sync-and-chunk-protocol.md       # High-throughput historical 8B activity sync
│   ├── 05-device-hardware-matrix.md        # Huami/Zepp device codenames & internal IDs
│   ├── 06-cloud-api-endpoints.md           # Huami Cloud REST APIs (Auth, Keys, Sync)
│   └── 07-chunked-endpoint-protocol.md     # Modern Zepp OS Chunked Transport & ECDH
├── code-reference/                         # Production-ready implementation snippets
│   └── android-kotlin/                     # Clean Kotlin code ready for Android Studio
│       ├── AmazfitConstants.kt             # Protocol UUIDs, opcodes, and regex constants
│       ├── AmazfitCrypto.kt                # AES-128 challenge encryption and key generator
│       ├── BleScanner.kt                   # Low-latency BLE discovery with hardware filtering
│       ├── BleConnectionManager.kt         # Reactive GATT connection & auth state machine
│       ├── TelemetryParser.kt              # Byte-level parsers for steps, battery, HR, 8B samples
│       └── models/
│           └── Models.kt                   # Data models (AuthState, RealtimeActivity, etc.)
└── tools/                                  # Local verification scripts (Python)
    ├── verify_auth_crypto.py               # AES-128 ECB challenge-response test vector validator
    ├── parse_telemetry.py                  # CLI decoder for raw hex packets & 8B samples
    └── ble_inspect.py                      # Bleak-based BLE service inspection utility
```

---

## 📖 Protocol Documentation Map

1. [**`docs/01-ble-architecture.md`**](docs/01-ble-architecture.md): Complete catalog of primary and secondary GATT services, handles, permissions, and MTU 512 negotiation.
2. [**`docs/02-authentication-handshake.md`**](docs/02-authentication-handshake.md): Step-by-step cryptographic sequence diagram showing initial pairing (`0x01`), challenge retrieval (`0x02`), AES-128-ECB challenge transformation, and response verification (`0x03`).
3. [**`docs/03-realtime-telemetry-protocol.md`**](docs/03-realtime-telemetry-protocol.md): Byte layouts, little-endian binary decoding, scaling factors, and update frequencies for real-time steps, active calories, distance, battery, and PPG heart rate.
4. [**`docs/04-sync-and-chunk-protocol.md`**](docs/04-sync-and-chunk-protocol.md): Analysis of the bulk sync engine (`0x0004` & `0x0005`), `FetchType` catalog, 15-minute timezone encoding, 8-byte minute sample records (`ActivitySample`), and ACK modes (`0x09` keep vs `0x01` clear).
5. [**`docs/05-device-hardware-matrix.md`**](docs/05-device-hardware-matrix.md): Exhaustive table of Huami device models, internal code numbers, hardware generation tags, and BLE advertising signatures.
6. [**`docs/06-cloud-api-endpoints.md`**](docs/06-cloud-api-endpoints.md): REST endpoints for Huami login, app token retrieval, and automated AuthKey recovery from the official cloud backend.
7. [**`docs/07-chunked-endpoint-protocol.md`**](docs/07-chunked-endpoint-protocol.md): Modern Zepp OS multiplexed framing protocol on `0x0016`/`0x0017`, `SECT163R2` ECDH key exchange, dynamic XOR message keys, and endpoint tables.

---

## 🛠 Testing & Verification Tools

```bash
# Verify AES-128 challenge encryption against known test vectors
python3 tools/verify_auth_crypto.py

# Decode sample realtime telemetry hex packet
python3 tools/parse_telemetry.py --steps "E8030000B004000014000000"
# Output: Steps: 1,000 | Distance: 1,200 m | Calories: 20 kcal

# Decode sample historical minute activity sample (8 bytes)
python3 tools/parse_telemetry.py --activity-sample "01141E4800010100"
# Output: Category: 1 | Intensity: 20 | Steps: 30 | Heart Rate: 72 BPM | Asleep: YES | Deep: YES
```

---

## ⚠️ Disclaimer

This project is created strictly for educational, research, and interoperability purposes under fair use. All product names, logos, and brands (Zepp, Amazfit, Huami, Monaco) are property of their respective owners.
