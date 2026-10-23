# Gemini Android App Builder: Complete Protocol Blueprint & Implementation Guide

This document is the **master blueprint and instruction prompt** designed specifically for **Gemini Studio (Google AI Studio)** or any AI Android coding assistant. It contains comprehensive, step-by-step instructions, protocol mechanics, byte structures, and architectural requirements needed to build a complete Android companion application for **Amazfit Balance (Monaco)** and **Zepp OS** devices.

---

## 📋 Master Prompt for Gemini Studio / AI App Builder

```markdown
# Role & Architecture Blueprint
You are an expert Android systems and UI engineer specializing in Kotlin 2.0+, Jetpack Compose, Material Design 3, Kotlin Coroutines/StateFlow, Android BLE GATT architecture, and Room SQLite persistence.

You are constructing a standalone, open-source, private Android companion application for the **Amazfit Balance** smartwatch (Internal Codename: `Monaco`, Zepp OS 3.x/4.x). The application replaces the official Zepp app by connecting directly to the watch over Bluetooth Low Energy (BLE), managing all device connections, authenticating cryptographically, receiving real-time telemetry, synchronizing historical biometrics, and controlling device features.

---

## 1. Android Configuration & Permissions

### `AndroidManifest.xml` Requirements:
```xml
<!-- BLE Permissions for Android 12+ (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Legacy BLE for Android 11 and lower -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Background Connection & Notification Permissions -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />

<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

### Foreground Service:
To prevent Android from killing the BLE connection in the background, run a `ForegroundService` with `foregroundServiceType="connectedDevice"` displaying a persistent low-priority notification with current connection state, step count, and battery level.

---

## 2. Protocol Specification & Step-by-Step Operations

### A. Device Discovery & Identification
* **Filter UUID:** Base Service `0000fee0-0000-1000-8000-00805f9b34fb`
* **Device Name Regex:** `^(Amazfit Balance|Monaco)[-_ ][A-Fa-f0-9]{4}$` (The 4 hex characters match the last 2 bytes of the MAC address).
* **Zepp Enum Identifiers:** `MILI_MONACO` (`8519936`), `MILI_MONACO_WN` (`8519937`), `MILI_MONACO_W` (`8519939`).

---

### B. Connection Lifecycle & MTU Negotiation
1. **Connect:** Call `bluetoothDevice.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)`.
2. **MTU 512 Negotiation (CRITICAL):**
   When `onConnectionStateChange()` reports `BluetoothProfile.STATE_CONNECTED`:
   * **IMMEDIATELY** call `gatt.requestMtu(512)`.
   * **DO NOT** discover services yet.
3. **Service Discovery:**
   In `onMtuChanged(gatt, mtu, status)`:
   * Verify negotiated MTU is $\ge 512$.
   * Call `gatt.discoverServices()`.

---

### C. Authentication Handshake (Dual-Mode Support)

The watch withholds all biometrics until authenticated. The app must implement both handshake modes:

#### Mode 1: Direct GATT AES-128-ECB Handshake (Base Telemetry Unlock)
* **Auth Service:** `0000fee1-0000-1000-8000-00805f9b34fb` (or fallback `0000fee0-...`)
* **Auth Characteristic:** `00000009-0000-3512-2118-0009af100700` (Properties: Write, Notify)
* **CCCD:** `00002902-0000-1000-8000-00805f9b34fb`

**Step-by-Step Execution:**
1. **Subscribe:** Write `[0x01, 0x00]` to CCCD descriptor `0x2902` on Characteristic `0x0009`.
2. **Pairing vs Reconnecting:**
   * **If New Pairing:**
     * Generate random 16 bytes: `authKey = ByteArray(16).apply { SecureRandom().nextBytes(this) }`.
     * Write to `0x0009`: `[0x01, 0x00] + authKey` (18 bytes total).
     * Watch screen vibrates and prompts user to tap checkmark.
     * When user taps confirm, watch emits notification: `[0x10, 0x01, 0x01]`.
     * Persist `authKey` securely in `EncryptedSharedPreferences`.
   * **If Reconnecting (Key already known):**
     * Write to `0x0009`: `[0x02, 0x00]` (or `[0x02, 0x00, 0x02, 0x01, 0x00]` for Zepp OS 3+).
3. **Watch Challenge:** Watch sends notification: `[0x10, 0x02, 0x01] + <16-byte random challenge>`.
4. **Encrypt Challenge:** Compute `AES/ECB/NoPadding(challenge, authKey)`:
   ```kotlin
   val cipher = Cipher.getInstance("AES/ECB/NoPadding")
   cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(authKey, "AES"))
   val encrypted = cipher.doFinal(challenge) // 16 bytes
   ```
5. **Send Verification:** Write to `0x0009`: `[0x03, 0x00] + encrypted` (18 bytes).
6. **Result Notification:**
   * `[0x10, 0x03, 0x01]`: **SUCCESS!** Device is now authenticated.
   * `[0x10, 0x03, 0x04]`: **FAIL (Key mismatch).**

---

#### Mode 2: Modern Zepp OS Chunked Multiplexed Session (ECDH SECT163R2)
Used for advanced features (weather, notifications, alarms, mini-apps, music controls) over:
* **TX Characteristic:** `00000016-0000-3512-2118-0009af100700` (Write)
* **RX Characteristic:** `00000017-0000-3512-2118-0009af100700` (Notify)

**Step-by-Step Execution:**
1. Enable notifications on `0x0017` via CCCD `0x2902`.
2. Generate EC Keypair on curve **`SECT163R2`**.
3. **Stage 1 Request:** Send chunk packet to Endpoint `AUTH` (`0x0082`):
   `[0x04, 0x02, 0x00, 0x02] + PublicEC.X (24B LE) + PublicEC.Y (24B LE)`.
4. **Stage 1 Response:** Receive notification on `0x0017`:
   `[Status 0x01, RemoteRandom (16B), RemotePublicEC (48B)]`.
5. **Key Derivation:**
   * Derive shared secret: `shared_ec = PrivateEC.exchange(RemotePublicEC)`.
   * Derive Session AES Key: `SessionKey[i] = shared_ec[i + 8] xor authKey[i]` (for `i` in `0..15`).
   * Sequence Number: `int.from_bytes(shared_ec[0..3], "big")`.
6. **Stage 2 Verification:** Send:
   `[0x05] + AES_ECB(RemoteRandom, authKey) + AES_ECB(RemoteRandom, SessionKey)`.
7. **Stage 2 Result:** Receive `[Status 0x01]`. The chunked transport is authenticated.
8. **Dynamic Message Encryption:** Subsequent packets use per-transaction key:
   `MessageKey[i] = SessionKey[i] xor handle`.

---

### D. Real-Time Telemetry Management
Once authenticated, subscribe to live streams on Service `0x0000fee0-0000-1000-8000-00805f9b34fb`:

1. **Real-time Steps, Distance & Calories:**
   * **Characteristic:** `00000007-0000-3512-2118-0009af100700`
   * Enable notification via CCCD `0x2902`.
   * **Byte Parsing:**
     ```kotlin
     val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
     val steps = buffer.int             // Offset 0..3: Steps count
     val distanceMeters = buffer.int    // Offset 4..7: Distance in meters
     val calories = buffer.int          // Offset 8..11: Active kcal
     ```
2. **Battery Status:**
   * **Characteristic:** `00000006-0000-3512-2118-0009af100700`
   * Enable notification & perform initial GATT read.
   * **Byte Parsing:**
     ```kotlin
     val level = data[0].toInt() and 0xFF
     val isCharging = if (data.size > 2) data[2].toInt() == 1 else false
     ```
3. **Live PPG Heart Rate:**
   * **Service:** `0000180d-0000-1000-8000-00805f9b34fb`
   * **Measurement Characteristic:** `00002a37-0000-1000-8000-00805f9b34fb` (Notify)
   * **Control Point:** `00002a39-0000-1000-8000-00805f9b34fb` (Write `[0x15, 0x01, 0x01]` to force 1-second continuous sampling; `[0x15, 0x01, 0x00]` to stop).
   * **Parsing:**
     ```kotlin
     val flags = data[0].toInt()
     val is16Bit = (flags and 0x01) != 0
     val bpm = if (is16Bit) (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
               else data[1].toInt() and 0xFF
     ```

---

### E. Historical Bulk Data Synchronization

To sync historical activity and sleep without draining watch battery:
* **Control / Metadata Endpoint:** `00000004-0000-3512-2118-0009af100700` (Write, Notify)
* **Data Stream Endpoint:** `00000005-0000-3512-2118-0009af100700` (Notify)

#### Protocol Execution:
1. Enable notifications on both `0x0004` and `0x0005`.
2. **Initiate Request:** Write to `0x0004`:
   ```
   [0x01, FetchType, Year_LSB, Year_MSB, Month, Day, Hour, Min, TZ_MSB, TZ_LSB]
   ```
   * `FetchType = 0x01` (`ACTIVITY` minute-by-minute)
   * `Timezone Offset`: Calculated in 15-minute units as big-endian int16:
     `val tzUnits = (timezone.getOffset(System.currentTimeMillis()) / 1000 / 60 / 15).toShort()`
3. **Metadata Response:** Watch notifies on `0x0004`:
   `[0x10, 0x01, Status (0x01), TotalLength (4B LE), StartTimestamp (8B), Unknown (1B)]`.
4. **Trigger Stream:** App writes to `0x0004`: `[0x02]` (`FETCH_DATA`).
5. **Receive Stream Bursts on `0x0005`:**
   * Each packet has: `[SequenceCounter (1 byte 0..255)] + [Payload Chunk]`.
   * Reassemble chunks in a `ByteArrayOutputStream`.
6. **Stream Finished:** Watch notifies on `0x0004`: `[0x10, 0x02, Status (0x01)]`.
7. **Acknowledge:** Write to `0x0004`:
   * Write `[0x03, 0x09]` to **keep data on watch** (recommended non-destructive read).
   * Write `[0x03, 0x01]` to **purge/clear synced data from watch flash**.

#### Decoding 8-Byte Minute Activity Records (`FetchType.ACTIVITY = 0x01`):
The reassembled payload consists of sequential 8-byte records:
```kotlin
data class MinuteRecord(
    val timestamp: Long,
    val kind: Int,          // 0 = Rest, 1 = Walking, 2 = Running, 3..7 = Sleep
    val intensity: Int,     // Accelerometer raw intensity (0..255)
    val steps: Int,         // Steps taken during this specific minute
    val heartRate: Int,     // BPM (0xFF = off wrist / invalid)
    val isAsleep: Boolean,  // true if minute is classified as sleep
    val isDeepSleep: Boolean,// true if N3 slow-wave sleep
    val isRemSleep: Boolean // true if REM sleep
)
```

---

### F. Device Management Functions (Chunked Multiplexing on `0x0016`)

Packets sent to `0x0016` use the 11-byte chunk header:
`[0x03, Flags, 0x00, Handle, Count=0, Length (4B LE), Endpoint (2B LE)] + Payload`.

1. **Find Watch (Ring/Vibrate):**
   * Endpoint: `0x001A` (`FIND_DEVICE`)
   * Payload: `[0x01]` (Start ringing), `[0x02]` (Stop ringing)
2. **Push Notifications (Caller ID, SMS, Apps):**
   * Endpoint: `0x001E` (`NOTIFICATIONS`)
   * Payload: Formatted UTF-8 text with title, app package name, and message body.
3. **Weather Forecast Push:**
   * Endpoint: `0x000E` (`WEATHER`)
   * Payload: 7-day high/low temperatures, weather condition icons, UV index.
4. **Music Control & Playback Status:**
   * Endpoint: `0x001B` (`MUSIC`)
   * Send track title, artist name, duration, and playback status (Playing / Paused).

---

### G. Assisted GPS (A-GPS) Injection (`UIHH` Container)

To enable 5-second GPS lock for outdoor runs:
1. Download `cep_7days.zip` (Almanac) and `lle_1week.zip` (Long-Term Ephemeris) from Zepp cloud.
2. Build the **`UIHH` binary container**:
   * Master Header: `b"UIHH"` + `[0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01]` + CRC32 (4B) + 6 zero bytes + Length (4B) + 6 zero bytes.
   * Body Section: Concatenated file chunks with header `[0x01, FileID, Length (4B LE), CRC32 (4B LE)]` + content.
   * Constellation IDs: `0x05` (GPS Almanac), `0x0F` (GLONASS), `0x86` (BeiDou LLE), `0x87` (GPS LLE), `0x88` (GLONASS LLE), `0x89` (Galileo LLE), `0x8A` (QZSS LLE).
3. Push the `UIHH` container to the watch via the high-throughput chunk service.

---

### H. Cloud AuthKey Extraction (Optional Setup Helper)
If the user has an existing Zepp account and does not want to re-pair or root:
1. Encrypt URL-encoded credentials with **AES-128-CBC** (`Key = b"xeNtBVqzDc6tuNTh"`, `IV = b"MAAAYAAAAAAAAABg"`).
2. POST to `https://api-user-us2.zepp.com/v2/registrations/tokens` with header `x-hm-ekv: 1`.
3. Intercept HTTP `303` redirect `Location` header to retrieve `access_token`.
4. Exchange for `app_token` at `https://api-mifit-us2.zepp.com/v2/client/login`.
5. Call `https://api-mifit.zepp.com/users/{user_id}/devices?channel=a100900101016` to retrieve the registered 16-byte hex `authKey`.

---

## 3. Recommended Project Structure for Android Studio

```
app/src/main/java/com/openbalance/companion/
├── data/
│   ├── ble/
│   │   ├── AmazfitConstants.kt         // All UUIDs, opcodes, and endpoints
│   │   ├── AmazfitCrypto.kt            // AES-128-ECB challenge encryption & ECDH
│   │   ├── BleScanner.kt               // Hardware-filtered device discovery
│   │   ├── BleConnectionManager.kt     // GATT connection lifecycle & Auth state machine
│   │   ├── ChunkedTransportManager.kt  // 0x0016 / 0x0017 multiplexed framing & AES session
│   │   ├── BulkSyncManager.kt          // 0x0004 / 0x0005 historical activity & sleep fetcher
│   │   ├── AgpsManager.kt              // UIHH A-GPS container builder
│   │   ├── TelemetryParser.kt          // Steps, battery, HR, 8-byte minute samples
│   │   └── model/
│   │       ├── AuthState.kt
│   │       ├── RealtimeActivity.kt
│   │       ├── BatteryInfo.kt
│   │       └── MinuteRecord.kt
│   ├── db/
│   │   ├── AppDatabase.kt              // Room SQLite Database
│   │   ├── entity/DailyActivity.kt
│   │   ├── entity/HeartRateSample.kt
│   │   └── dao/ActivityDao.kt
│   └── service/
│       └── BleForegroundService.kt     // Persistent background connection service
└── ui/
    ├── MainActivity.kt                 // Single-activity Jetpack Compose host
    ├── navigation/NavGraph.kt
    ├── screens/
    │   ├── scan/ScanScreen.kt          // BLE device scanning & pairing
    │   ├── dashboard/DashboardScreen.kt// Live steps, calories, battery, HR cards
    │   ├── history/HistoryScreen.kt    // Daily step charts & sleep stage breakdown
    │   └── settings/SettingsScreen.kt  // AuthKey config, Find Watch, Sync trigger
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

---

## 4. UI Design Guidelines (Jetpack Compose + Material 3)
* **Dark Fitness Aesthetic:** High-contrast AMOLED black theme (`#0D1117`), vibrant neon accents (Cyan `#00E5FF` for steps, Red/Coral `#FF5252` for heart rate, Electric Amber `#FFD600` for active calories, Emerald `#00E676` for battery).
* **Live Dashboard:**
  * Animated circular progress gauges for Daily Steps (target: 10,000).
  * Real-time heart rate pulse chart with active BPM badge.
  * Watch battery status card with charging indicator and last-charged timestamp.
  * Connection Status Chip (Connected / Authenticating / Disconnected).
* **Action Center:**
  * One-tap "Sync Now" button that triggers historical sync.
  * "Find Watch" button to vibrate the watch.
  * A-GPS update button with expiration status.

Construct the code cleanly, modularly, and with thorough error handling!
```
