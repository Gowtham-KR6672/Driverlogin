package com.example.drivertracking.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.example.drivertracking.MainActivity
import com.example.drivertracking.R
import com.example.drivertracking.data.local.LocationEntity
import com.example.drivertracking.data.remote.LocationPayload
import com.example.drivertracking.data.repository.TrackingRepository
import com.example.drivertracking.util.Constants
import com.example.drivertracking.util.LocationUtils
import com.example.drivertracking.util.SessionManager
import com.example.drivertracking.util.SocketManager
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject lateinit var trackingRepository: TrackingRepository
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var socketManager: SocketManager

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var windowManager: WindowManager

    private var floatingView: View? = null
    private var lastLocation: Location? = null
    private var totalDistance = 0f

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isTracking = false
    private var heartbeatJob: Job? = null
    @Volatile private var lastSentAt = 0L

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val newLocation = locationResult.lastLocation ?: return
                
                if (LocationUtils.isValidLocationUpdate(lastLocation, newLocation)) {
                    lastLocation?.let {
                        totalDistance += LocationUtils.calculateDistance(
                            it.latitude, it.longitude, newLocation.latitude, newLocation.longitude
                        )
                    }
                    lastLocation = newLocation
                    processLocation(newLocation)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START_TRACKING) {
            startTracking()
        } else if (action == ACTION_STOP_TRACKING) {
            stopTracking()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        if (isTracking) return
        isTracking = true

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        socketManager.connect()

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, Constants.LOCATION_UPDATE_INTERVAL)
            .setMinUpdateIntervalMillis(Constants.LOCATION_FASTEST_INTERVAL)
            .setMaxUpdateDelayMillis(Constants.LOCATION_MAX_WAIT_TIME)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        setupFloatingBubble()
        startHeartbeat()

        // Save state to preferences indicating we should restart on boot
        getSharedPreferences("TrackingPrefs", Context.MODE_PRIVATE).edit().putBoolean("isTracking", true).apply()
    }

    private fun stopTracking() {
        isTracking = false
        heartbeatJob?.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        socketManager.disconnect()
        removeFloatingBubble()
        
        getSharedPreferences("TrackingPrefs", Context.MODE_PRIVATE).edit().putBoolean("isTracking", false).apply()
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Battery percentage (0-100) and charging state for the admin's Live Tracking page.
    // BatteryManager needs no permission; unsupported devices report an out-of-range level.
    private fun readBattery(): Pair<Int?, Boolean?> {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return null to null
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return (if (level in 0..100) level else null) to batteryManager.isCharging
    }

    // While parked, Android stops delivering fixes and tiny moves are filtered out above,
    // so nothing would be sent. Re-send the last known position (speed 0) once a minute.
    @SuppressLint("MissingPermission")
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                delay(Constants.LOCATION_HEARTBEAT_INTERVAL)
                if (System.currentTimeMillis() - lastSentAt < Constants.LOCATION_HEARTBEAT_INTERVAL) continue
                val known = lastLocation ?: try {
                    com.google.android.gms.tasks.Tasks.await(fusedLocationClient.lastLocation, 10, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    null
                }
                known?.let { processLocation(Location(it).apply { speed = 0f }) }
            }
        }
    }

    private fun processLocation(location: Location) {
        val driverId = sessionManager.getDriverId()
        if (driverId == -1) return
        lastSentAt = System.currentTimeMillis()

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = sdf.format(Date(location.time))

        // Emit live to socket
        socketManager.emitLocation(
            driverId = driverId,
            lat = location.latitude,
            lng = location.longitude,
            speed = location.speed,
            accuracy = location.accuracy,
            bearing = location.bearing,
            timestamp = timestamp
        )

        serviceScope.launch {
            val (batteryLevel, batteryCharging) = readBattery()
            val payload = LocationPayload(
                driverId = driverId,
                latitude = location.latitude,
                longitude = location.longitude,
                speed = location.speed,
                accuracy = location.accuracy,
                bearing = location.bearing,
                timestamp = timestamp,
                batteryLevel = batteryLevel,
                batteryCharging = batteryCharging
            )
            
            // Try to send to API
            val sent = trackingRepository.sendLocation(payload)
            if (!sent) {
                // Save offline if API fails
                val entity = LocationEntity(
                    driverId = payload.driverId,
                    latitude = payload.latitude,
                    longitude = payload.longitude,
                    speed = payload.speed,
                    accuracy = payload.accuracy,
                    bearing = payload.bearing,
                    timestamp = payload.timestamp
                )
                trackingRepository.saveLocationLocally(entity)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingBubble() {
        // Create an empty simple ImageView programmatically if layout not defined
        // Normally you inflate a layout. We'll make a simple circle programmatically for now.
        if (floatingView != null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION.SDK_INT && !android.provider.Settings.canDrawOverlays(this)) {
            return // No permission
        }

        val size = (56 * resources.displayMetrics.density).toInt()
        
        floatingView = ImageView(this).apply {
            val bg = android.graphics.drawable.GradientDrawable()
            bg.shape = android.graphics.drawable.GradientDrawable.OVAL
            bg.setColor(android.graphics.Color.parseColor("#3B82F6"))
            bg.setStroke((2 * resources.displayMetrics.density).toInt(), android.graphics.Color.WHITE)
            background = bg
            
            setImageResource(R.mipmap.ic_launcher_round)
            setPadding(0, 0, 0, 0)
            elevation = (8 * resources.displayMetrics.density)
            clipToOutline = true
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION.SDK_INT)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        floatingView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = (event.rawX - initialTouchX).toInt()
                    val diffY = (event.rawY - initialTouchY).toInt()
                    // If it was a click (not drag), open MainActivity
                    if (Math.abs(diffX) < 10 && Math.abs(diffY) < 10) {
                        val intent = Intent(this@LocationTrackingService, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingView, params)
        floatingView?.visibility = View.GONE // Hide initially, show when app goes to background if needed. Or keep visible.
        floatingView?.visibility = View.VISIBLE // As per requirement, persist while active
    }

    private fun removeFloatingBubble() {
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatingView = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION.SDK_INT) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Driver Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Driver Tracking Active")
            .setContentText("Live location tracking in progress")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use your own icon
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Tracking", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("LocationTrackingService", "App UI swiped away, service continuing.")
    }

    override fun onDestroy() {
        super.onDestroy()
        heartbeatJob?.cancel()
        removeFloatingBubble()
        socketManager.disconnect()
    }

    companion object {
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
        private const val CHANNEL_ID = "tracking_channel"
        private const val NOTIFICATION_ID = 1
    }
}
