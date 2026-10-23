# Zepp / Huami Cloud REST API Reference

While companion applications can operate in a 100% offline, local-first mode, Zepp's cloud backend provides programmatic retrieval of the `auth_key`, user profile metrics, cloud backup synchronization, and Assisted GPS (A-GPS) orbit almanacs.

Cross-referenced and verified against `argrento/huami-token`.

---

## 1. Cloud Architecture & Hosts

| Host Endpoint | Region / Role |
| :--- | :--- |
| `https://api-user-us2.zepp.com` | User registration, credential exchange, token generation (US region) |
| `https://api-user.zepp.com` | User registration & token generation (Default / Global) |
| `https://api-mifit-us2.zepp.com` | App session authentication, device bindings, A-GPS files |
| `https://api-mifit.zepp.com` | Account device list, registered AuthKeys, firmware updates |
| `https://api-analytics.zepp.com` | Historical workouts, GPS tracks, sleep analysis export |

---

## 2. Encrypted Authentication Sequence (Modern Zepp 9.x / 10.x)

Modern Zepp REST APIs require credentials submitted to `/v2/registrations/tokens` to be encrypted via **AES-128-CBC** using static client keys before transmission over HTTPS.

### Encryption Parameters:
* **Algorithm:** AES (128-bit)
* **Mode:** CBC (Cipher Block Chaining)
* **Key:** `b"xeNtBVqzDc6tuNTh"`
* **IV:** `b"MAAAYAAAAAAAAABg"`
* **Padding:** PKCS#7
* **Required Header:** `x-hm-ekv: 1`

---

### Step 1: Encrypted Credential Exchange

The application prepares the URL-encoded query dictionary:
```python
payload = {
    "emailOrPhone": "user@example.com",
    "password": "UserPassword",
    "state": "REDIRECTION",
    "client_id": "HuaMi",
    "redirect_uri": "https://s3-us-west-2.amazonaws.com/hm-registration/successsignin.html",
    "region": "us-west-2",
    "token": ["access", "refresh"],
    "country_code": "US"
}
```

The URL-encoded string is encrypted with `Key` and `IV`, and posted as raw binary:

```http
POST https://api-user-us2.zepp.com/v2/registrations/tokens HTTP/1.1
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
x-hm-ekv: 1
app_name: com.huami.midong
appplatform: android_phone
v: 2.0
user-agent: Zepp/9.12.5 (Pixel 4; Android 12)

<RAW AES-128-CBC ENCRYPTED BYTES>
```

**Response Behavior:**
The server responds with a **`303 See Other`** HTTP redirect without following. The redirect `Location` header contains the access and refresh tokens:
```
Location: https://s3-us-west-2.amazonaws.com/hm-registration/successsignin.html?access=TOKEN_STRING&refresh=REFRESH_STRING
```

---

### Step 2: Session Exchange (`login_token` & `app_token`)

With the `access_token` extracted from the redirect query parameters:

```http
POST https://api-mifit-us2.zepp.com/v2/client/login HTTP/1.1
Content-Type: application/x-www-form-urlencoded; charset=UTF-8
app_name: com.huami.midong

code=ACCESS_TOKEN_STRING
&grant_type=access_token
&device_id=PHONE_ANDROID_ID
&device_model=android_phone
&app_version=9.12.5
&country_code=US
&lang=en
&dn=api-mifit.zepp.com,api-user.zepp.com,api-watch.zepp.com,auth.zepp.com
&source=com.huami.watch.hmwatchmanager:9.12.5:151689
```

**Response (JSON):**
```json
{
  "result": "ok",
  "token_info": {
    "login_token": "LOGIN_TOKEN_HEX",
    "app_token": "APP_TOKEN_STRING",
    "user_id": "1234567890"
  }
}
```

---

## 3. Retrieving Registered Device Keys (`auth_key`)

This endpoint returns all devices currently paired to the account, including the 16-byte cryptographic `auth_key`:

```http
GET https://api-mifit.zepp.com/users/1234567890/devices?enableMultiDevice=true&enableMultiDeviceOnMultiType=true&channel=a100900101016 HTTP/1.1
apptoken: APP_TOKEN_STRING
x-request-id: 3fa85f64-5717-4562-b3fc-2c963f66afa6
```

**Response (JSON):**
```json
{
  "items": [
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

The `authKey` field contains the exact 16-byte hex key required for the local Bluetooth GATT challenge-response handshake.
