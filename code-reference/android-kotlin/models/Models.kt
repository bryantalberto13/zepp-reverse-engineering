package org.openbalance.data.ble.model

sealed class AuthState {
    data object Disconnected : AuthState()
    data object Connecting : AuthState()
    data object DiscoveringServices : AuthState()
    data object EnablingNotifications : AuthState()
    data object PairingNewKey : AuthState()
    data object RequestingChallenge : AuthState()
    data object EncryptingChallenge : AuthState()
    data object VerifyingResponse : AuthState()
    data class Authenticated(val deviceAddress: String, val deviceName: String) : AuthState()
    data class Failed(val error: String) : AuthState()
}

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

data class RealtimeActivity(
    val steps: Int = 0,
    val distanceMeters: Int = 0,
    val calories: Int = 0
)

data class BatteryInfo(
    val percentage: Int = 0,
    val isCharging: Boolean = false,
    val lastChargedTimestamp: Long = 0L
)

data class HeartRateReading(
    val bpm: Int = 0,
    val isContactDetected: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

data class MinuteActivityRecord(
    val timestamp: Long,
    val kind: Int,
    val intensity: Int,
    val steps: Int,
    val heartRate: Int,
    val isAsleep: Boolean,
    val isDeepSleep: Boolean,
    val isRemSleep: Boolean
)
