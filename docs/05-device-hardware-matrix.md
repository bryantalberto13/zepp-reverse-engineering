# Device Hardware & Identification Matrix

This matrix correlates the internal hardware identifiers, decompiled enum entries (`zhk.java`), marketing names, BLE advertisement signatures, and display configurations across Huami and Amazfit smartwatches.

---

## 1. Amazfit Balance Series (`Monaco`)

The **Amazfit Balance** is identified in Zepp by the internal codename `Monaco`:

| Property | Specification | Source / Decompiled Reference |
| :--- | :--- | :--- |
| **Enum Tag** | `MILI_MONACO` | `zhk.a6 = new zhk("MILI_MONACO", 219, HMDeviceType, 8519936)` |
| **Enum Tag (WN Variant)**| `MILI_MONACO_WN` | `zhk.b6 = new zhk("MILI_MONACO_WN", ..., 8519937)` |
| **Enum Tag (W Variant)** | `MILI_MONACO_W` | `zhk.c6 = new zhk("MILI_MONACO_W", ..., 8519939)` |
| **Advertisement Names** | `Amazfit Balance`, `Monaco`, `MonacoW`, `MonacoWN` | `w5l.java` line 157 |
| **Advertisement Regex** | `^(Amazfit Balance\|Monaco)-[a-fA-F0-9]{4}$` | `w5l.java` line 158 |
| **Display Spec** | 1.50" AMOLED, 480 × 480 circular screen | Zepp OS 3.0+ target |
| **NFC & Mic/Speaker** | Supported (Bluetooth Calling, Alexa / Zepp Flow) | |

---

## 2. Other Key Huami / Amazfit Device Mappings

Extracted from `zhk.java` enum definitions:

| Enum Name | Enum ID | Internal Codename | Consumer Marketing Name |
| :--- | :--- | :--- | :--- |
| `MILI_MONACO` | `8519936` | Monaco | **Amazfit Balance** |
| `MILI_MONACO_WN` | `8519937` | Monaco WN | **Amazfit Balance (NFC Global)** |
| `MILI_MONACO_W` | `8519939` | Monaco W | **Amazfit Balance (WiFi/BT)** |
| `MILI_ALTAI` | `8454400` | Altai | **Amazfit Cheetah (Round)** |
| `MILI_ALTAI_W` | `8454401` | Altai W | **Amazfit Cheetah Pro** |
| `MILI_ETNA_W` | `8388865` | Etna W | **Amazfit GTR 4** |
| `MILI_LEWISTON` | `8192000` | Lewiston | **Amazfit GTS 4** |
| `MILI_NEW_YORK` | `7864320` | NewYork | **Amazfit T-Rex 2** |
| `MILI_EVEREST` | `8323072` | Everest | **Amazfit T-Rex Ultra / Falcon** |
| `MILI_DOLOMITES` | `7995392` | Dolomites | **Amazfit Band 7** |

---

## 3. BLE Advertising Packet Structure

Amazfit devices advertise standard manufacturer data packets containing their hardware identity:

```
+------------+-----------------------------------+--------------------+
| Length (B) | Field Type                        | Payload            |
+------------+-----------------------------------+--------------------+
| 0x02       | 0x01 (Flags)                      | 0x06 (General Disc)|
| 0x03       | 0x03 (16-bit Service UUIDs)       | 0xE0, 0xFE (FEE0)  |
| 0x11       | 0x09 (Complete Local Name)        | "Amazfit Balance-XXXX" |
| 0x0E       | 0xFF (Manufacturer Specific Data) | 0x57, 0x01 ...     |
+------------+-----------------------------------+--------------------+
```

* **Device Name Suffix:** The 4 hex digits after the hyphen in `Amazfit Balance-XXXX` match the last two bytes of the watch's Bluetooth MAC address in uppercase hexadecimal format.
* **Scan Filter:** To discover only Amazfit Balance devices without scanning everything in range:
  ```kotlin
  val filter = ScanFilter.Builder()
      .setServiceUuid(ParcelUuid.fromString("0000fee0-0000-1000-8000-00805f9b34fb"))
      .build()
  ```
