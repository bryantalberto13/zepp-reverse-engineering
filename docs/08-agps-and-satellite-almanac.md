# Assisted GPS (A-GPS) & Satellite Almanac Protocol

To achieve rapid GPS satellite fixes (< 5 seconds) during outdoor workouts without forcing the watch receiver to spend 5–15 minutes downloading ephemeris data over the satellite radio, Amazfit watches rely on Assisted GPS (A-GPS) packages sent over BLE.

---

## 1. Cloud Download Endpoints

The Zepp cloud provides A-GPS orbit packages for multi-GNSS constellations (GPS, GLONASS, BeiDou, Galileo, QZSS):

```http
GET https://api-mifit-us2.zepp.com/apps/com.xiaomi.hm.health/fileTypes/{file_type}/files?userid={user_id}&appid={app_id}&channel=a100900101016 HTTP/1.1
apptoken: APP_TOKEN_STRING
```

### Supported File Types:
* `AGPS_ALM`: Contains 7-day Almanac archives (`cep_7days.zip`)
* `LLE`: Contains 1-week Long-term Orbit / Ephemeris archives (`lle_1week.zip`)
* `AGPSZIP`: Compressed AGPS bundle
* `EPO` / `LTO`: MediaTek / Sony GNSS chip ephemeris formats

---

## 2. Satellite Constellation Identifiers

Extracted from the A-GPS packager (`argrento/huami-token`):

| File Name | Byte ID | Constellation / Almanac |
| :--- | :--- | :--- |
| `gps_alm.bin` | `0x05` | GPS Almanac |
| `gln_alm.bin` | `0x0F` | GLONASS Almanac |
| `lle_bds.lle` | `0x86` | BeiDou Long-Term Orbit |
| `lle_gps.lle` | `0x87` | GPS Long-Term Orbit |
| `lle_glo.lle` | `0x88` | GLONASS Long-Term Orbit |
| `lle_gal.lle` | `0x89` | Galileo Long-Term Orbit |
| `lle_qzss.lle`| `0x8A` | QZSS Long-Term Orbit |

---

## 3. The `UIHH` Binary Container Format

Before transmitting A-GPS data to the watch over the high-throughput chunk service (`0xFEE7` or `0x0016`), the companion app bundles the constellation files into a **`UIHH` binary container**:

### Master Container Layout:
```
+---------------+-----------------------+---------------+-----------------+---------------+
| Magic (4B)    | Container Flags (8B)  | Body CRC (4B) | Padding 1 (6B)  | Body Len (4B) |
| "UIHH"        | 04 00 00 00 00 00 00 01| uint32 LE     | 00 00 00 00 00 00 | uint32 LE     |
+---------------+-----------------------+---------------+-----------------+---------------+
| Padding 2 (6B)| Body Section (Concatenated File Chunks...)                              |
| 00 00 00 ...  | [Chunk 1] + [Chunk 2] + [Chunk 3] ...                                   |
+---------------+-------------------------------------------------------------------------+
```

### Individual File Chunk Header:
Each constellation file inside the body is prefixed by a 10-byte header:
```
+---------+---------+-------------------+-------------------+
| Byte 0  | Byte 1  | Bytes 2 - 5       | Bytes 6 - 9       |
+---------+---------+-------------------+-------------------+
| Version | File ID | Content Length    | CRC32 Checksum    |
| (0x01)  | (Byte)  | (uint32 LE)       | (uint32 LE)       |
+---------+---------+-------------------+-------------------+
```

### Python Packaging Implementation:
```python
import struct, zlib

def build_uihh_package(files_dict: dict[int, bytes]) -> bytes:
    body = bytearray()
    
    for file_id, content in files_dict.items():
        chunk_header = struct.pack("<2B2I", 0x01, file_id, len(content), zlib.crc32(content) & 0xFFFFFFFF)
        body.extend(chunk_header + content)
        
    master_header = (
        b"UIHH" +
        bytes([0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01]) +
        struct.pack("<I", zlib.crc32(body) & 0xFFFFFFFF) +
        bytes(6) +
        struct.pack("<I", len(body)) +
        bytes(6)
    )
    
    return bytes(master_header + body)
```
