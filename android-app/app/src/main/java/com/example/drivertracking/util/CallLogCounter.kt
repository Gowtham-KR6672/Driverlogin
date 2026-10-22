package com.example.drivertracking.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.drivertracking.data.remote.CallEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class CallCounts(val incoming: Int, val outgoing: Int, val missed: Int)

/** Call counts plus the answered/made calls (used to detect calls while driving). */
data class CallSummary(val counts: CallCounts, val events: List<CallEvent>)

/**
 * Reads the phone's calls during a work entry: type, start time and length only. No numbers,
 * names or contacts are read or sent.
 */
@Singleton
class CallLogCounter @Inject constructor(@ApplicationContext private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    /** Calls since [sinceMillis] (the work start), or null if unknown or the Call log permission is off. */
    fun summarizeSince(sinceMillis: Long?): CallSummary? {
        if (sinceMillis == null || !hasPermission()) return null
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(sinceMillis.toString()),
                "${CallLog.Calls.DATE} ASC"
            )?.use { cursor ->
                var incoming = 0
                var outgoing = 0
                var missed = 0
                val events = mutableListOf<CallEvent>()
                val typeColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                while (cursor.moveToNext()) {
                    val start = cursor.getLong(dateColumn)
                    val duration = cursor.getLong(durationColumn)
                    when (cursor.getInt(typeColumn)) {
                        CallLog.Calls.INCOMING_TYPE -> {
                            incoming++
                            if (duration > 0) events += CallEvent(start, duration, "incoming")
                        }
                        CallLog.Calls.OUTGOING_TYPE -> {
                            outgoing++
                            if (duration > 0) events += CallEvent(start, duration, "outgoing")
                        }
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> missed++
                    }
                }
                CallSummary(CallCounts(incoming, outgoing, missed), events)
            }
        } catch (e: SecurityException) {
            null
        }
    }
}
