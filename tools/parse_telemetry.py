#!/usr/bin/env python3
"""
CLI utility to parse raw hex payloads from Zepp / Amazfit BLE characteristics.
Supports:
  - Realtime steps / calories / distance (Characteristic 0x0007)
  - Battery status (Characteristic 0x0006)
  - Heart rate measurement (Characteristic 0x2A37)
"""

import argparse
import struct
import datetime

def parse_steps(hex_str: str):
    clean = hex_str.replace(" ", "").replace("0x", "")
    data = bytes.fromhex(clean)
    if len(data) < 4:
        print("[!] Error: Steps payload must be at least 4 bytes")
        return

    steps = struct.unpack("<I", data[0:4])[0]
    distance = struct.unpack("<I", data[4:8])[0] if len(data) >= 8 else 0
    calories = struct.unpack("<I", data[8:12])[0] if len(data) >= 12 else 0

    print("--- [Real-time Steps Telemetry (0x0007)] ---")
    print(f"  Raw Hex:    {clean.upper()}")
    print(f"  Steps:      {steps:,}")
    print(f"  Distance:   {distance:,} meters ({(distance / 1000.0):.2f} km)")
    print(f"  Calories:   {calories:,} kcal")

def parse_battery(hex_str: str):
    clean = hex_str.replace(" ", "").replace("0x", "")
    data = bytes.fromhex(clean)
    if len(data) < 1:
        print("[!] Error: Battery payload cannot be empty")
        return

    level = data[0]
    state = data[1] if len(data) > 1 else 0
    is_charging = (data[2] == 1) if len(data) > 2 else False
    last_charged_ts = struct.unpack("<I", data[3:7])[0] if len(data) >= 7 else None

    print("--- [Battery Information (0x0006)] ---")
    print(f"  Raw Hex:      {clean.upper()}")
    print(f"  Level:        {level}%")
    print(f"  Status Flag:  {state}")
    print(f"  Charging:     {'YES (Plugged In)' if is_charging else 'NO (Discharging)'}")
    if last_charged_ts and last_charged_ts > 0:
        dt = datetime.datetime.fromtimestamp(last_charged_ts)
        print(f"  Last Charged: {dt.strftime('%Y-%m-%d %H:%M:%S')}")

def parse_hr(hex_str: str):
    clean = hex_str.replace(" ", "").replace("0x", "")
    data = bytes.fromhex(clean)
    if len(data) < 2:
        print("[!] Error: Heart rate payload must be at least 2 bytes")
        return

    flags = data[0]
    is_16bit = bool(flags & 0x01)
    contact = (flags >> 1) & 0x03
    contact_str = {0: "Not Supported", 1: "Not Supported", 2: "No Contact", 3: "Contact Detected"}.get(contact, "Unknown")

    if is_16bit and len(data) >= 3:
        bpm = struct.unpack("<H", data[1:3])[0]
    else:
        bpm = data[1]

    print("--- [Heart Rate Measurement (0x2A37)] ---")
    print(f"  Raw Hex:      {clean.upper()}")
    print(f"  Heart Rate:   {bpm} BPM")
    print(f"  Skin Contact: {contact_str}")

def main():
    parser = argparse.ArgumentParser(description="Parse Amazfit/Zepp BLE Telemetry Packets")
    parser.add_argument("--steps", help="Hex string from characteristic 0x0007")
    parser.add_argument("--battery", help="Hex string from characteristic 0x0006")
    parser.add_argument("--hr", help="Hex string from characteristic 0x2A37")

    args = parser.parse_args()
    if args.steps:
        parse_steps(args.steps)
    if args.battery:
        parse_battery(args.battery)
    if args.hr:
        parse_hr(args.hr)
    if not (args.steps or args.battery or args.hr):
        parser.print_help()

if __name__ == "__main__":
    main()
