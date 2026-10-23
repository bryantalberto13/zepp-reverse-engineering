#!/usr/bin/env python3
"""
Inspects Amazfit / Zepp BLE services, characteristics, and descriptors on Linux
using the Bleak library.
"""

import asyncio
import sys

try:
    from bleak import BleakScanner, BleakClient
except ImportError:
    print("Please install bleak: pip install bleak")
    sys.exit(1)

AMAZFIT_NAMES = ["Amazfit Balance", "Monaco"]

async def find_device():
    print("[*] Scanning for Amazfit / Zepp devices...")
    devices = await BleakScanner.discover(timeout=5.0)
    target = None
    for d in devices:
        name = d.name or ""
        if any(target_prefix in name for target_prefix in AMAZFIT_NAMES):
            print(f"[+] Found device: {d.name} [{d.address}] RSSI: {d.rssi}")
            target = d
            break
    return target

async def inspect(address: str):
    print(f"[*] Connecting to {address}...")
    async with BleakClient(address) as client:
        print(f"[+] Connected: {client.is_connected}")
        print("\n--- Discovered GATT Services & Characteristics ---")
        for service in client.services:
            print(f"\n[Service] {service.uuid} ({service.description})")
            for char in service.characteristics:
                props = ", ".join(char.properties)
                print(f"   └── [Char] {char.uuid} [{props}] ({char.description})")
                for desc in char.descriptors:
                    print(f"         └── [Descriptor] {desc.uuid}")

async def main():
    if len(sys.argv) > 1:
        addr = sys.argv[1]
    else:
        device = await find_device()
        if not device:
            print("[-] No Amazfit device found. Pass MAC address as argument: python3 ble_inspect.py XX:XX:XX:XX:XX:XX")
            return
        addr = device.address

    await inspect(addr)

if __name__ == "__main__":
    asyncio.run(main())
