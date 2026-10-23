# Zepp / Huami Cloud REST API Reference

While companion applications can operate in a 100% offline, local-first mode, Zepp's cloud backend provides programmatic retrieval of the `auth_key`, user profile metrics, and cloud backup synchronization.

---

## 1. Cloud Architecture & Hosts

| Host Endpoint | Role / Usage |
| :--- | :--- |
| `https://account.huami.com` | User authentication, OAuth tokens, login sessions |
| `https://api-user.huami.com` | App token registration and session delegation |
| `https://api-mifit.huami.com` | Bound device metadata, device keys, firmware info |
| `https://api-analytics.huami.com` | Workout history, GPS routes, sleep analysis exports |

---

## 2. Authentication Flow

### Step 1: User Login
```http
POST https://account.huami.com/v2/client/login
Content-Type: application/x-www-form-urlencoded

dn=account.huami.com
&app_name=com.huami.midong
&source=com.huami.watch.hmwatchmanager
&email=user%40example.com
&password=URL_ENCODED_PASSWORD
```

**Response (JSON):**
```json
{
  "result": "ok",
  "token_info": {
    "login_token": "TOKEN_STRING_HERE",
    "user_id": "1234567890"
  }
}
```

### Step 2: Grant App Token
```http
GET https://api-user.huami.com/registrations/1234567890/tokens?client_id=HuaMi&token=TOKEN_STRING_HERE&state=REDIRECTION
```

**Response (JSON):**
```json
{
  "token_info": {
    "app_token": "APP_TOKEN_STRING"
  }
}
```

---

## 3. Retrieving Registered Device Keys (`auth_key`)

This endpoint returns all devices currently paired to the account, including the 16-byte cryptographic `auth_key` generated during watch provisioning:

```http
GET https://api-mifit.huami.com/users/1234567890/devices
Header: apptoken: APP_TOKEN_STRING
```

**Sample Response Body:**
```json
{
  "code": 1,
  "data": [
    {
      "macAddress": "C4:4E:AC:XX:XX:XX",
      "deviceType": 8519936,
      "deviceSource": 219,
      "authKey": "0x4A8E73B901C46D1952EA8F03672D8E1B",
      "deviceName": "Amazfit Balance",
      "firmwareVersion": "3.22.7.1"
    }
  ]
}
```

> **Note:** The `authKey` field contains the exact 16-byte AES key (prefixed with `0x`) required for the local Bluetooth GATT challenge-response handshake!
