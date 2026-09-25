package com.geoguardtelemetry

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.util.concurrent.atomic.AtomicReference

class GeoGuardTelemetryModule(
    reactContext: ReactApplicationContext
) : NativeGeoguardTelemetrySpec(reactContext) {

    override fun getName(): String = MODULE_NAME

    override fun startLocationTracking(requestConfigurationJson: String) {
        val appContext = reactApplicationContext.applicationContext

        appContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PERSISTED_CONFIG, requestConfigurationJson)
            .apply()

        reactApplicationContext.currentActivity?.runOnUiThread {
            Toast.makeText(
                reactApplicationContext,
                "🚀 GeoGuard Activated!",
                Toast.LENGTH_SHORT
            ).show()
        }

        val serviceIntent =
            Intent(appContext, GeoGuardTrackingService::class.java).apply {
                putExtra(
                    EXTRA_CONFIG_PAYLOAD,
                    requestConfigurationJson
                )
            }

        ContextCompat.startForegroundService(
            appContext,
            serviceIntent
        )
    }

    override fun stopLocationTracking() {
        val appContext = reactApplicationContext.applicationContext

        reactApplicationContext.currentActivity?.runOnUiThread {
            Toast.makeText(
                reactApplicationContext,
                "🛑 Deactivating GeoGuard...",
                Toast.LENGTH_SHORT
            ).show()
        }

        val serviceIntent =
            Intent(appContext, GeoGuardTrackingService::class.java)

        appContext.stopService(serviceIntent)

        activeModuleInstance.set(null)
    }

    fun broadcastTelemetryEvent(
        channelName: String,
        dataPayload: WritableMap
    ) {
        if (reactApplicationContext.hasActiveReactInstance()) {
            reactApplicationContext
                .getJSModule(
                    DeviceEventManagerModule.RCTDeviceEventEmitter::class.java
                )
                .emit(channelName, dataPayload)
        }
    }

    companion object {
        const val MODULE_NAME = "GeoGuardTelemetryModule"

        const val PREFS_NAME =
            "com.geoguardtelemetry.STORAGE_PREFS"

        const val KEY_PERSISTED_CONFIG =
            "CACHED_MASTER_CONFIG"

        const val EXTRA_CONFIG_PAYLOAD =
            "CONFIG_PAYLOAD_STRING"

        private val activeModuleInstance =
            AtomicReference<GeoGuardTelemetryModule?>(null)

        fun getSharedInstance(): GeoGuardTelemetryModule? =
            activeModuleInstance.get()
    }

    override fun initialize() {
        super.initialize()
        activeModuleInstance.set(this)
    }

    override fun invalidate() {
        activeModuleInstance.compareAndSet(this, null)
        super.invalidate()
    }

    override fun addListener(eventName: String) {
        // Required by NativeEventEmitter
    }

    override fun removeListeners(count: Double) {
        // Required by NativeEventEmitter
    }
}