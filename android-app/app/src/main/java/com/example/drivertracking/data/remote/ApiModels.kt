package com.example.drivertracking.data.remote

import com.google.gson.annotations.SerializedName
import java.util.Locale
import kotlin.math.roundToInt

data class LoginRequest(
    val username: String,
    val password: String // Adjust fields based on actual Flask backend requirements
)

data class LoginResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("token") val token: String?,
    @SerializedName("driver_id") val driverId: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("role") val role: String?,
    // Admins only: "full", "edit" or "view"
    @SerializedName("access_level") val accessLevel: String? = null,
    @SerializedName("message") val message: String?
)

data class LocationPayload(
    @SerializedName("driver_id") val driverId: Int,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val accuracy: Float,
    val bearing: Float,
    val timestamp: String,
    // Only sent with live locations; points synced later from the offline queue leave these null.
    @SerializedName("battery_level") val batteryLevel: Int? = null,
    @SerializedName("battery_charging") val batteryCharging: Boolean? = null
)

data class LocationResponse(
    val success: Boolean,
    val message: String? = null
)

data class HomeStats(
    @SerializedName("total_hours_display") val totalHoursDisplay: String,
    @SerializedName("total_km") val totalKm: String,
    @SerializedName("total_entries") val totalEntries: Int
)

data class HomeResponse(
    val success: Boolean,
    val stats: HomeStats? = null,
    val message: String? = null
)

data class WorkEntry(
    val id: Int,
    @SerializedName("work_given_person") val workGivenPerson: String,
    @SerializedName("vehicle_type") val vehicleType: String,
    @SerializedName("next_start_time") val nextStartTime: String?,
    @SerializedName("distance_display") val distanceDisplay: String,
    val remarks: String,
    // Work start in epoch millis; the phone counts calls from this moment.
    @SerializedName("start_epoch_ms") val startEpochMs: Long? = null,
    @SerializedName("trip_id") val tripId: Int? = null,
    @SerializedName("top_speed_kmh") val topSpeedKmh: Double? = null
)

data class ActiveWorkResponse(
    val success: Boolean,
    @SerializedName("has_active_work") val hasActiveWork: Boolean,
    val entry: WorkEntry? = null,
    val message: String? = null
)

data class StartWorkRequest(
    @SerializedName("driver_id") val driverId: Int,
    @SerializedName("work_given_person") val workGivenPerson: String,
    @SerializedName("vehicle_type") val vehicleType: String,
    val remarks: String,
    @SerializedName("start_lat") val startLat: Double,
    @SerializedName("start_lng") val startLng: Double
)

data class GenericResponse(
    val success: Boolean,
    val message: String? = null
)

data class EndWorkRequest(
    @SerializedName("driver_id") val driverId: Int,
    @SerializedName("end_lat") val endLat: Double? = null,
    @SerializedName("end_lng") val endLng: Double? = null,
    val remarks: String? = null,
    @SerializedName("incoming_calls") val incomingCalls: Int? = null,
    @SerializedName("outgoing_calls") val outgoingCalls: Int? = null,
    @SerializedName("missed_calls") val missedCalls: Int? = null
)

// Ends the current entry here and starts the next one for another person (same vehicle).
data class ContinueWorkRequest(
    @SerializedName("work_given_person") val workGivenPerson: String,
    val remarks: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracy: Float? = null,
    @SerializedName("incoming_calls") val incomingCalls: Int? = null,
    @SerializedName("outgoing_calls") val outgoingCalls: Int? = null,
    @SerializedName("missed_calls") val missedCalls: Int? = null
)

data class HistoryEntry(
    val id: Int,
    @SerializedName("work_given_person") val workGivenPerson: String,
    @SerializedName("vehicle_type") val vehicleType: String,
    @SerializedName("next_start_time") val nextStartTime: String?,
    @SerializedName("end_time") val endTime: String?,
    val duration: String,
    @SerializedName("distance_display") val distanceDisplay: String,
    val remarks: String,
    @SerializedName("top_speed_kmh") val topSpeedKmh: Double? = null,
    @SerializedName("avg_speed_kmh") val avgSpeedKmh: Double? = null,
    @SerializedName("incoming_calls") val incomingCalls: Int? = null,
    @SerializedName("outgoing_calls") val outgoingCalls: Int? = null,
    @SerializedName("missed_calls") val missedCalls: Int? = null,
    // Entries continued from one another share a trip id.
    @SerializedName("trip_id") val tripId: Int? = null
)

fun HistoryEntry.topSpeedText(): String = topSpeedKmh?.let { "${it.roundToInt()} km/h" } ?: "-"

fun HistoryEntry.avgSpeedText(): String = avgSpeedKmh?.let { String.format(Locale.US, "%.1f km/h", it) } ?: "-"

// "-" when the phone didn't report calls (website entry or Call log permission off).
fun HistoryEntry.callsText(): String =
    if (incomingCalls == null) "-" else "In $incomingCalls · Out $outgoingCalls · Missed $missedCalls"

data class HistoryResponse(
    val success: Boolean,
    val entries: List<HistoryEntry>? = null,
    val message: String? = null
)

data class AdminDashboardResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("employee_count") val employeeCount: Int,
    @SerializedName("travel_hours_display") val travelHoursDisplay: String,
    @SerializedName("travel_km") val travelKm: Double,
    @SerializedName("active_entries") val activeEntries: List<AdminActiveEntry>?
)

data class AdminActiveEntry(
    @SerializedName("id") val id: Int,
    @SerializedName("username") val username: String,
    @SerializedName("vehicle_type") val vehicleType: String,
    @SerializedName("work_given_person") val workGivenPerson: String,
    @SerializedName("distance_display") val distanceDisplay: String,
    @SerializedName("start_time") val startTime: String?
)

data class AdminUsersResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("users") val users: List<AdminUser>?
)

data class AdminUser(
    @SerializedName("id") val id: Int,
    @SerializedName("username") val username: String,
    @SerializedName("role") val role: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("access_level") val accessLevel: String? = null
)

data class AdminWorkResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("entries") val entries: List<HistoryEntry>?
)
