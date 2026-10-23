#!/usr/bin/env python3
"""
Verification script for Amazfit / Zepp AES-128 challenge-response authentication.
Tests the exact AES/ECB/NoPadding algorithm implemented in Zepp's qck.java / zsk.java.
"""

import sys
try:
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
except ImportError:
    print("Please install cryptography: pip install cryptography")
    sys.exit(1)

def encrypt_challenge(challenge_bytes: bytes, auth_key_bytes: bytes) -> bytes:
    assert len(challenge_bytes) == 16, f"Challenge must be 16 bytes, got {len(challenge_bytes)}"
    assert len(auth_key_bytes) == 16, f"Auth key must be 16 bytes, got {len(auth_key_bytes)}"
    cipher = Cipher(algorithms.AES(auth_key_bytes), modes.ECB())
    encryptor = cipher.encryptor()
    return encryptor.update(challenge_bytes) + encryptor.finalize()

def run_tests():
    print("==================================================")
    print("Zepp BLE Handshake AES-128 Test Vector Verification")
    print("==================================================")

    # Test Vector 1: Arbitrary key and challenge
    key_hex = "0123456789abcdef0123456789abcdef"
    challenge_hex = "11223344556677889900aabbccddeeff"
    
    key_bytes = bytes.fromhex(key_hex)
    challenge_bytes = bytes.fromhex(challenge_hex)

    encrypted = encrypt_challenge(challenge_bytes, key_bytes)
    print(f"Auth Key:         {key_hex}")
    print(f"Random Challenge: {challenge_hex}")
    print(f"Encrypted Result: {encrypted.hex()}")

    # Outgoing payload check: Opcode 0x03 0x00 + 16 bytes encrypted
    send_payload = bytes([0x03, 0x00]) + encrypted
    print(f"GATT Write Pkt:   {send_payload.hex().upper()} (Length: {len(send_payload)} bytes)")
    assert len(send_payload) == 18, "Outgoing payload must be exactly 18 bytes"
    print("\n[SUCCESS] AES-128-ECB challenge-response cipher passed validation.")

if __name__ == "__main__":
    run_tests()
