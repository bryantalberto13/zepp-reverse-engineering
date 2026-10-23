package org.openbalance.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.openbalance.data.ble.model.AuthState
import org.openbalance.data.ble.model.BatteryInfo
import org.openbalance.data.ble.model.HeartRateReading
import org.openbalance.data.ble.model.RealtimeActivity

class BleConnectionManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var activeGatt: BluetoothGatt? = null
    private var currentAuthKey: ByteArray = ByteArray(16)
    private var isPairingNewDevice: Boolean = false

    private val _authState = MutableStateFlow<AuthState>(AuthState.Disconnected)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _batteryInfo = MutableStateFlow(BatteryInfo())
    val batteryInfo: StateFlow<BatteryInfo> = _batteryInfo.asStateFlow()

    private val _realtimeActivity = MutableStateFlow(RealtimeActivity())
    val realtimeActivity: StateFlow<RealtimeActivity> = _realtimeActivity.asStateFlow()

    private val _heartRate = MutableStateFlow(HeartRateReading())
    val heartRate: StateFlow<HeartRateReading> = _heartRate.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _authState.value = AuthState.Connecting
                    // Step 1: Negotiate MTU 512 for high throughput
                    gatt.requestMtu(512)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _authState.value = AuthState.Disconnected
                    activeGatt?.close()
                    activeGatt = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            _authState.value = AuthState.DiscoveringServices
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _authState.value = AuthState.Failed("Service discovery failed: $status")
                return
            }

            // Find Auth Service (0xFEE1 preferred, fallback to 0xFEE0)
            val authService = gatt.getService(AmazfitConstants.SERVICE_MIBAND_2)
                ?: gatt.getService(AmazfitConstants.SERVICE_MIBAND_1)

            if (authService == null) {
                _authState.value = AuthState.Failed("Auth Service (0xFEE1 / 0xFEE0) not found")
                return
            }

            val authChar = authService.getCharacteristic(AmazfitConstants.CHAR_AUTH)
            if (authChar == null) {
                _authState.value = AuthState.Failed("Auth characteristic 0x0009 not found")
                return
            }

            _authState.value = AuthState.EnablingNotifications
            enableNotification(gatt, authChar)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _authState.value = AuthState.Failed("Failed to enable descriptor notifications")
                return
            }

            if (descriptor.characteristic.uuid == AmazfitConstants.CHAR_AUTH) {
                val authChar = descriptor.characteristic
                if (isPairingNewDevice) {
                    _authState.value = AuthState.PairingNewKey
                    val payload = AmazfitCrypto.buildSendKeyPayload(currentAuthKey)
                    writeCharacteristic(gatt, authChar, payload)
                } else {
                    _authState.value = AuthState.RequestingChallenge
                    val payload = AmazfitCrypto.buildRequestRandomAuthPayload()
                    writeCharacteristic(gatt, authChar, payload)
                }
            }
        }

        @Deprecated("Android 12 and below")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleNotification(gatt, characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(gatt, characteristic.uuid, value)
        }

        @Deprecated("Android 12 and below")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleNotification(gatt, characteristic.uuid, characteristic.value)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleNotification(gatt, characteristic.uuid, value)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleNotification(gatt: BluetoothGatt, uuid: java.util.UUID, data: ByteArray?) {
        if (data == null || data.isEmpty()) return

        when (uuid) {
            AmazfitConstants.CHAR_AUTH -> handleAuthResponse(gatt, data)
            AmazfitConstants.CHAR_REALTIME_STEPS -> {
                _realtimeActivity.value = TelemetryParser.parseRealtimeActivity(data)
            }
            AmazfitConstants.CHAR_BATTERY -> {
                _batteryInfo.value = TelemetryParser.parseBattery(data)
            }
            AmazfitConstants.CHAR_HEART_RATE_MEASUREMENT -> {
                _heartRate.value = TelemetryParser.parseHeartRate(data)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleAuthResponse(gatt: BluetoothGatt, data: ByteArray) {
        if (data.size < 3 || data[0] != AmazfitConstants.AUTH_OP_RESPONSE) return

        val opcode = data[1]
        val status = data[2]

        when (opcode) {
            AmazfitConstants.AUTH_OP_SEND_KEY -> {
                if (status == AmazfitConstants.AUTH_RESULT_SUCCESS) {
                    _authState.value = AuthState.RequestingChallenge
                    val authChar = getAuthCharacteristic(gatt)
                    if (authChar != null) {
                        writeCharacteristic(gatt, authChar, AmazfitCrypto.buildRequestRandomAuthPayload())
                    }
                } else {
                    _authState.value = AuthState.Failed("User rejected or timeout pairing new key")
                }
            }

            AmazfitConstants.AUTH_OP_REQUEST_RANDOM -> {
                if (status == AmazfitConstants.AUTH_RESULT_SUCCESS && data.size >= 19) {
                    _authState.value = AuthState.EncryptingChallenge
                    val challenge = data.copyOfRange(3, 19)
                    val encrypted = AmazfitCrypto.encryptChallenge(challenge, currentAuthKey)

                    _authState.value = AuthState.VerifyingResponse
                    val authChar = getAuthCharacteristic(gatt)
                    if (authChar != null) {
                        writeCharacteristic(gatt, authChar, AmazfitCrypto.buildEncryptedAuthPayload(encrypted))
                    }
                } else {
                    _authState.value = AuthState.Failed("Watch did not return a valid 16-byte challenge")
                }
            }

            AmazfitConstants.AUTH_OP_SEND_ENCRYPTED -> {
                if (status == AmazfitConstants.AUTH_RESULT_SUCCESS) {
                    val name = gatt.device.name ?: "Amazfit Balance"
                    _authState.value = AuthState.Authenticated(gatt.device.address, name)

                    // Successfully authenticated! Safe to enable biometric streams
                    subscribeToTelemetry(gatt)
                } else {
                    _authState.value = AuthState.Failed("Authentication failed: AES key mismatch")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeToTelemetry(gatt: BluetoothGatt) {
        val baseService = gatt.getService(AmazfitConstants.SERVICE_MIBAND_1) ?: return

        // 1. Subscribe to Real-time Steps (0x0007)
        baseService.getCharacteristic(AmazfitConstants.CHAR_REALTIME_STEPS)?.let { char ->
            enableNotification(gatt, char)
        }

        // 2. Read initial Battery Status (0x0006)
        baseService.getCharacteristic(AmazfitConstants.CHAR_BATTERY)?.let { char ->
            gatt.readCharacteristic(char)
        }

        // 3. Subscribe to Heart Rate if present
        val hrService = gatt.getService(AmazfitConstants.SERVICE_HEART_RATE)
        hrService?.getCharacteristic(AmazfitConstants.CHAR_HEART_RATE_MEASUREMENT)?.let { hrChar ->
            enableNotification(gatt, hrChar)
        }
    }

    private fun getAuthCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
        val service = gatt.getService(AmazfitConstants.SERVICE_MIBAND_2)
            ?: gatt.getService(AmazfitConstants.SERVICE_MIBAND_1)
        return service?.getCharacteristic(AmazfitConstants.CHAR_AUTH)
    }

    @SuppressLint("MissingPermission")
    private fun enableNotification(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(char, true)
        val descriptor = char.getDescriptor(AmazfitConstants.CCCD_DESCRIPTOR) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, payload: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = payload
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectAndAuthenticate(address: String, authKeyHex: String, isNewPairing: Boolean) {
        val adapter = bluetoothAdapter ?: return
        val device: BluetoothDevice = adapter.getRemoteDevice(address)

        this.currentAuthKey = if (authKeyHex.isNotBlank()) {
            AmazfitCrypto.hexToBytes(authKeyHex)
        } else {
            AmazfitCrypto.generateRandomAuthKey()
        }
        this.isPairingNewDevice = isNewPairing

        _authState.value = AuthState.Connecting
        activeGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        activeGatt?.disconnect()
        activeGatt?.close()
        activeGatt = null
        _authState.value = AuthState.Disconnected
    }
}
