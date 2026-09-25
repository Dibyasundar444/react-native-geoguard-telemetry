package com.geoguardtelemetry

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.facebook.react.bridge.Arguments
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GeoGuardTrackingService : Service() {

    private val trackingThreadPool = Executors.newSingleThreadExecutor()
    private lateinit var locationProviderClient: FusedLocationProviderClient
    private var trackingCallbackInstance: LocationCallback? = null
    private var operationalConfigurationJson: String? = null

    // Default senior battery optimization profiles configurations
    private var configurationIntervalMs = 5000L
    private var boundaryClampIntervalMs = 2000L
    private var trackingDistanceDisplacementFilterMeters = 5.0f

    override fun onCreate() {
        super.onCreate()
        locationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        initializeForegroundNotificationPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            operationalConfigurationJson = intent.getStringExtra(GeoGuardTelemetryModule.EXTRA_CONFIG_PAYLOAD)
        } else {
            val localDiskStorage = getSharedPreferences(GeoGuardTelemetryModule.PREFS_NAME, Context.MODE_PRIVATE)
            operationalConfigurationJson = localDiskStorage.getString(GeoGuardTelemetryModule.KEY_PERSISTED_CONFIG, null)
            Log.i(LOG_TAG, "GeoGuard successfully rehydrated manifest out of internal memory cache storage block.")
        }

        extractOperationalParameters()
        initializeLocationCapturePipeline()

        return START_STICKY
    }

    private fun initializeForegroundNotificationPanel() {
        val channelId = "geoguard_telemetry_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val trackingChannel = NotificationChannel(channelId, "GeoGuard Core Telemetry", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
            description = "Maintains persistent pipeline syncing real-time fleet coordinate pings."
        }
        notificationManager.createNotificationChannel(trackingChannel)

        val persistentNotification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("GeoGuard System: Operational")
            .setContentText("Actively processing and syncing secure telemetry route lines.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(PERSISTENT_NOTIFICATION_ID, persistentNotification)
    }

    private fun extractOperationalParameters() {
        operationalConfigurationJson?.let {
            try {
                val manifestRoot = JSONObject(it)
                if (manifestRoot.has("options")) {
                    val trackingOptions = manifestRoot.getJSONObject("options")
                    if (trackingOptions.has("interval")) configurationIntervalMs = trackingOptions.getLong("interval")
                    if (trackingOptions.has("fastestInterval")) boundaryClampIntervalMs = trackingOptions.getLong("fastestInterval")
                    if (trackingOptions.has("distanceFilter")) trackingDistanceDisplacementFilterMeters = trackingOptions.getDouble("distanceFilter").toFloat()
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error parsing option configurations object parameters schemas", e)
            }
        }
    }

    private fun initializeLocationCapturePipeline() {
        terminateLocationPipelineUpdates()

        val locationRequestSettings = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, configurationIntervalMs)
            .setMinUpdateIntervalMillis(boundaryClampIntervalMs)
            .setMinUpdateDistanceMeters(trackingDistanceDisplacementFilterMeters)
            .setWaitForAccurateLocation(true)
            .build()

        trackingCallbackInstance = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val resolvedCoordinates = locationResult.lastLocation ?: return
                trackingThreadPool.execute {
                    transmitTelemetryPacketToServer(resolvedCoordinates.latitude, resolvedCoordinates.longitude)
                }
            }
        }

        try {
            locationProviderClient.requestLocationUpdates(locationRequestSettings, trackingCallbackInstance!!, Looper.getMainLooper())
        } catch (permissionException: SecurityException) {
            Log.e(LOG_TAG, "Security permission mapping exception caught during pipeline registration", permissionException)
        }
    }

    private fun transmitTelemetryPacketToServer(latitudeVal: Double, longitudeVal: Double) {
        // Emit raw tracking data packet back into JS view layers first
        GeoGuardTelemetryModule.getSharedInstance()?.let { moduleInstance ->
            val coordinateMap = Arguments.createMap().apply {
                putDouble("latitude", latitudeVal)
                putDouble("longitude", longitudeVal)
                putDouble("timestamp", System.currentTimeMillis().toDouble())
            }
            moduleInstance.broadcastTelemetryEvent("onLocationChange", coordinateMap)
        }

        val fullConfigurationStr = operationalConfigurationJson ?: return
        var clientHttpSocket: HttpURLConnection? = null
        var endpointUrlTarget = ""

        try {
            val configObjectRoot = JSONObject(fullConfigurationStr)
            endpointUrlTarget = if (configObjectRoot.has("url")) configObjectRoot.getString("url") else return
            val standardHttpMethod = if (configObjectRoot.has("method")) configObjectRoot.getString("method").uppercase() else "POST"

            // Construct transactional payload dictionary from parameters
            val outboundPayloadJson = JSONObject()
            if (configObjectRoot.has("body")) {
                val configurationBodyObj = configObjectRoot.getJSONObject("body")
                configurationBodyObj.keys().forEach { key -> outboundPayloadJson.put(key, configurationBodyObj.get(key)) }
            }

            // Recursive Object Traversal: Locates custom target key indicators deep within nested arrays/objects
            fun traverseAndInjectCoordinates(jsonNode: JSONObject, lat: Double, lng: Double): Boolean {
                var injectionStatus = false
                val keyBuffer = mutableListOf<String>()
                val structuralKeys = jsonNode.keys()
                while (structuralKeys.hasNext()) { keyBuffer.add(structuralKeys.next()) }

                for (targetKey in keyBuffer) {
                    val structuralValue = jsonNode.get(targetKey)
                    if (structuralValue is JSONObject) {
                        if (traverseAndInjectCoordinates(structuralValue, lat, lng)) injectionStatus = true
                    } else {
                        if (targetKey.equals("lat", ignoreCase = true) || targetKey.equals("latitude", ignoreCase = true)) {
                            jsonNode.put(targetKey, lat)
                            injectionStatus = true
                        }
                        if (targetKey.equals("lng", ignoreCase = true) || targetKey.equals("long", ignoreCase = true) || targetKey.equals("longitude", ignoreCase = true)) {
                            jsonNode.put(targetKey, lng)
                            injectionStatus = true
                        }
                    }
                }
                return injectionStatus
            }

            val executionSuccessFlag = traverseAndInjectCoordinates(outboundPayloadJson, latitudeVal, longitudeVal)

            // Fallback Safety Protection Chain
            if (!executionSuccessFlag) {
                outboundPayloadJson.put("latitude", latitudeVal)
                outboundPayloadJson.put("longitude", longitudeVal)
            }

            if (!outboundPayloadJson.has("timestamp")) {
                outboundPayloadJson.put("timestamp", System.currentTimeMillis())
            }

            val payloadBytesArray = outboundPayloadJson.toString().toByteArray(Charsets.UTF_8)

            clientHttpSocket = (URL(endpointUrlTarget).openConnection() as HttpURLConnection).apply {
                requestMethod = standardHttpMethod
                connectTimeout = SOCKET_TIMEOUT_LIMIT_MS
                readTimeout = SOCKET_TIMEOUT_LIMIT_MS
                doOutput = true
                setChunkedStreamingMode(0)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")

                if (configObjectRoot.has("headers")) {
                    val dynamicHeadersBlock = configObjectRoot.getJSONObject("headers")
                    dynamicHeadersBlock.keys().forEach { targetHeaderKey ->
                        setRequestProperty(targetHeaderKey, dynamicHeadersBlock.getString(targetHeaderKey))
                    }
                }
            }

            BufferedOutputStream(clientHttpSocket.outputStream).use { networkStream ->
                networkStream.write(payloadBytesArray)
                networkStream.flush()
            }

            val httpServerResponseCode = clientHttpSocket.responseCode
            val activeNetworkStream = if (httpServerResponseCode in 200..299) clientHttpSocket.inputStream else clientHttpSocket.errorStream
            val serverStringMessageBytes = activeNetworkStream?.bufferedReader()?.use { it.readText() } ?: ""

            Log.d(LOG_TAG, "Network transaction completed successfully. Server Code: $httpServerResponseCode")

            // Push execution metrics downstream back to JS debugger runtime frames
            GeoGuardTelemetryModule.getSharedInstance()?.let { moduleInstance ->
                val runtimeResponseMap = Arguments.createMap().apply {
                    putBoolean("isSuccess", httpServerResponseCode in 200..299)
                    putInt("statusCode", httpServerResponseCode)
                    putString("resultBody", serverStringMessageBytes)
                    putString("apiUrl", endpointUrlTarget)
                    putString("payloadSent", outboundPayloadJson.toString())
                    putString("errorMessage", "")
                    putDouble("timestamp", System.currentTimeMillis().toDouble())
                }
                moduleInstance.broadcastTelemetryEvent("onNetworkResponse", runtimeResponseMap)
            }

        } catch (networkException: Exception) {
            Log.e(LOG_TAG, "Telemetry pipeline socket execution connection error exception raised", networkException)

            GeoGuardTelemetryModule.getSharedInstance()?.let { moduleInstance ->
                val diagnosticErrorMap = Arguments.createMap().apply {
                    putBoolean("isSuccess", false)
                    putInt("statusCode", -1)
                    putString("resultBody", "")
                    putString("apiUrl", endpointUrlTarget)
                    putString("payloadSent", "")
                    putString("errorMessage", networkException.message ?: "Network Sockets Timeout Exception")
                    putDouble("timestamp", System.currentTimeMillis().toDouble())
                }
                moduleInstance.broadcastTelemetryEvent("onNetworkResponse", diagnosticErrorMap)
            }
        } finally {
            clientHttpSocket?.disconnect()
        }
    }

    private fun terminateLocationPipelineUpdates() {
        trackingCallbackInstance?.let {
            locationProviderClient.removeLocationUpdates(it)
        }
        trackingCallbackInstance = null
    }

    override fun onDestroy() {
        terminateLocationPipelineUpdates()
        trackingThreadPool.shutdown()
        try {
            if (!trackingThreadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                trackingThreadPool.shutdownNow()
            }
        } catch (ie: InterruptedException) {
            trackingThreadPool.shutdownNow()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val LOG_TAG = "GeoGuardTrackingService"
        private const val PERSISTENT_NOTIFICATION_ID = 8899
        private const val SOCKET_TIMEOUT_LIMIT_MS = 10000
    }
}
