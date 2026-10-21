package com.example.drivertracking.util

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketManager @Inject constructor(
    private val sessionManager: SessionManager
) {
    private var socket: Socket? = null

    fun connect() {
        if (socket?.connected() == true) return
        
        try {
            val options = IO.Options()
            options.forceNew = true
            options.reconnection = true
            
            // If backend requires auth for socket:
            val token = sessionManager.getAuthToken()
            if (token != null) {
                options.query = "token=$token"
            }

            socket = IO.socket(Constants.BASE_URL, options)
            
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("SocketManager", "Connected to Socket.IO server")
            }
            
            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d("SocketManager", "Disconnected from Socket.IO server")
            }
            
            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e("SocketManager", "Connection error: ${args.contentToString()}")
            }

            socket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun emitLocation(
        driverId: Int,
        lat: Double,
        lng: Double,
        speed: Float,
        accuracy: Float,
        bearing: Float,
        timestamp: String
    ) {
        if (socket?.connected() == true) {
            val data = JSONObject().apply {
                put("driver_id", driverId)
                put("latitude", lat)
                put("longitude", lng)
                put("speed", speed)
                put("accuracy", accuracy)
                put("bearing", bearing)
                put("timestamp", timestamp)
            }
            socket?.emit("driver_location_update", data)
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }
}
