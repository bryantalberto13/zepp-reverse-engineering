#!/usr/bin/env python3
"""
Zepp / Amazfit Cloud Auth Key Extractor
Extracts the 16-byte Bluetooth LE Auth Key for watches already paired to Zepp.
"""

import sys
import getpass
import urllib.parse
import json
import requests
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives import padding

CLOUD_AES_KEY = b"xeNtBVqzDc6tuNTh"
CLOUD_AES_IV  = b"MAAAYAAAAAAAAABg"

REGIONS = {
    "us": {"auth": "https://api-user-us2.zepp.com", "mifit": "https://api-mifit-us2.zepp.com", "region": "us-west-2", "cc": "US"},
    "eu": {"auth": "https://api-user-de0.zepp.com", "mifit": "https://api-mifit-de0.zepp.com", "region": "eu-central-1", "cc": "DE"},
    "cn": {"auth": "https://api-user.huami.com",     "mifit": "https://api-mifit.huami.com",     "region": "cn-north-1",   "cc": "CN"},
    "sg": {"auth": "https://api-user-sg0.zepp.com", "mifit": "https://api-mifit-sg0.zepp.com", "region": "ap-southeast-1","cc": "SG"},
    "ru": {"auth": "https://api-user-ru0.zepp.com", "mifit": "https://api-mifit-ru0.zepp.com", "region": "ru-central-1",   "cc": "RU"},
}

def encrypt_payload(data_str: str) -> bytes:
    padder = padding.PKCS7(128).padder()
    padded_data = padder.update(data_str.encode('utf-8')) + padder.finalize()
    cipher = Cipher(algorithms.AES(CLOUD_AES_KEY), modes.CBC(CLOUD_AES_IV))
    encryptor = cipher.encryptor()
    return encryptor.update(padded_data) + encryptor.finalize()

def extract_keys():
    print("=" * 60)
    print("  Zepp / Amazfit Cloud Device Key Extractor")
    print("=" * 60)
    
    email = input("Zepp Account Email: ").strip()
    if not email:
        print("Error: Email is required.")
        return
    password = getpass.getpass("Zepp Account Password: ")
    if not password:
        print("Error: Password is required.")
        return

    print("\nSelect your account region:")
    print("  1) us (United States / Americas) [Default]")
    print("  2) eu (Europe)")
    print("  3) sg (Asia / Global)")
    print("  4) ru (Russia)")
    print("  5) cn (China)")
    choice = input("Region [1]: ").strip()
    region_map = {"1": "us", "2": "eu", "3": "sg", "4": "ru", "5": "cn", "": "us"}
    r_key = region_map.get(choice, "us")
    server = REGIONS[r_key]

    print(f"\n[1/3] Authenticating with Zepp Cloud ({r_key.upper()})...")
    
    payload = {
        "emailOrPhone": email,
        "password": password,
        "state": "REDIRECTION",
        "client_id": "HuaMi",
        "redirect_uri": "https://s3-us-west-2.amazonaws.com/hm-registration/successsignin.html",
        "region": server["region"],
        "token": "access",
        "country_code": server["cc"],
    }
    encoded_query = urllib.parse.urlencode(payload)
    encrypted_body = encrypt_payload(encoded_query)

    headers = {
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "x-hm-ekv": "1",
        "app_name": "com.huami.midong",
        "appplatform": "android_phone",
        "v": "2.0",
        "user-agent": "Zepp/9.12.5 (Pixel 4; Android 12)",
    }

    try:
        r = requests.post(f"{server['auth']}/v2/registrations/tokens", data=encrypted_body, headers=headers, allow_redirects=False)
        if r.status_code not in (302, 303):
            print(f"Login failed (HTTP {r.status_code}). Please verify your credentials.")
            return

        location = r.headers.get("Location", "")
        parsed = urllib.parse.urlparse(location)
        query_params = urllib.parse.parse_qs(parsed.query)
        access_token = query_params.get("access", [None])[0]

        if not access_token:
            print("Failed to retrieve access token from redirect.")
            return

        print("[2/3] Exchanging session tokens...")
        login_url = f"{server['mifit']}/v2/client/login"
        login_data = {
            "app_name": "com.huami.midong",
            "dn": f"api-user-{r_key}2.zepp.com,{server['mifit'].replace('https://', '')}",
            "grant_type": "access_token",
            "code": access_token
        }
        r2 = requests.post(login_url, data=login_data, headers={"app_name": "com.huami.midong", "v": "2.0"})
        r2_json = r2.json()
        token_info = r2_json.get("token_info", {})
        user_id = token_info.get("user_id")
        app_token = token_info.get("app_token")

        if not user_id or not app_token:
            print(f"Failed to obtain app_token: {r2_json}")
            return

        print("[3/3] Fetching paired devices and Auth Keys...")
        devices_url = f"{server['mifit']}/users/{user_id}/devices?enable_all=1"
        r3 = requests.get(devices_url, headers={"apptoken": app_token})
        items = r3.json().get("items", [])

        if not items:
            print("No paired devices found for this account.")
            return

        print("\n" + "=" * 60)
        print("  FOUND PAIRED DEVICES & AUTH KEYS")
        print("=" * 60)
        for d in items:
            raw_key = d.get("authKey", "")
            clean_key = raw_key.replace("0x", "").upper()
            name = d.get("deviceName", "Unknown Watch")
            mac = d.get("macAddress", "Unknown MAC")
            print(f"Device:   {name}")
            print(f"MAC:      {mac}")
            print(f"Auth Key: {clean_key}")
            print("-" * 60)

        print("\n--> Copy the 32-character Auth Key into OpenBalance under 'Existing Key'!\n")

    except Exception as e:
        print(f"Error during retrieval: {e}")

if __name__ == "__main__":
    extract_keys()
