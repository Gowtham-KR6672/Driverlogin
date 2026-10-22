package com.example.drivertracking.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class CallCounts(val incoming: Int, val outgoing: Int, val missed: Int)

/**
 * Counts the phone's calls during a work entry. Only the call type is read: no numbers,
 * names or durations leave the phone.
 */
@Singleton
class CallLogCounter @Inject constructor(@ApplicationContext private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    /** Calls since [sinceMillis] (the work start), or null if unknown or the Call log permission is off. */
    fun countSince(sinceMillis: Long?): CallCounts? {
        if (sinceMillis == null || !hasPermission()) return null
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.TYPE),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(sinceMillis.toString()),
                null
            )?.use { cursor ->
                var incoming = 0
                var outgoing = 0
                var missed = 0
                val typeColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                while (cursor.moveToNext()) {
                    when (cursor.getInt(typeColumn)) {
                        CallLog.Calls.INCOMING_TYPE -> incoming++
                        CallLog.Calls.OUTGOING_TYPE -> outgoing++
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> missed++
                    }
                }
                CallCounts(incoming, outgoing, missed)
            }
        } catch (e: SecurityException) {
            null
        }
    }
}
