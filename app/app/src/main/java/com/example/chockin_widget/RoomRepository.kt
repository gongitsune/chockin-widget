package com.example.chockin_widget

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Date
import javax.net.ssl.HttpsURLConnection

data class Member(val name: String, val avatar: String?, val status: String?)
data class Room(val members: List<Member>, val timestamp: Instant) {
    fun formattedTime(context: Context): String {
        val locale = context.resources.configuration.locales[0]
        val skeleton = if (DateFormat.is24HourFormat(context)) "MMMdHm" else "MMMdhm"
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        return SimpleDateFormat(pattern, locale).format(Date.from(timestamp))
    }
}

data class RoomState(val room: Room? = null, val failed: Boolean = false)

internal fun parseRoom(json: String): Room {
    val obj = JSONObject(json)
    val array = obj.getJSONArray("members")
    val members = List(array.length()) { i ->
        val item = array.getJSONObject(i)
        fun optional(key: String) =
            if (item.isNull(key)) null else item.getString(key).takeIf { it.isNotBlank() }
        Member(item.getString("displayName"), optional("avatarUrl"), optional("statusMessage"))
    }
    return Room(members, OffsetDateTime.parse(obj.getString("serverTime")).toInstant())
}

object RoomRepository {
    private val lock = Mutex()
    private fun prefs(context: Context) = context.getSharedPreferences("room", Context.MODE_PRIVATE)

    fun observe(context: Context) = callbackFlow {
        val prefs = prefs(context)
        fun publish() {
            val room =
                prefs.getString("json", null)?.let { runCatching { parseRoom(it) }.getOrNull() }
            trySend(RoomState(room, prefs.getBoolean("failed", false)))
        }

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> publish() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        publish()
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate().distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            val edit = prefs(context).edit()
            val success = try {
                val connection =
                    URL("https://chockin-proxy.k0ara.com/api/room").openConnection() as HttpsURLConnection
                val json = try {
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.setRequestProperty("Accept", "application/json")
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
                parseRoom(json)
                edit.putString("json", json)
                true
            } catch (_: IOException) {
                false
            } catch (_: JSONException) {
                false
            } catch (_: java.time.format.DateTimeParseException) {
                false
            }
            edit.putBoolean("failed", !success).apply()
            success
        }
    }
}
