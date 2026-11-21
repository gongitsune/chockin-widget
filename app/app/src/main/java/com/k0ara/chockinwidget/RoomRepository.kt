package com.k0ara.chockinwidget

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import androidx.core.content.edit
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
import java.text.BreakIterator
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Date
import javax.net.ssl.HttpsURLConnection

data class Member(val name: String, val avatar: String?, val status: String?) {
    val initial: String
        get() {
            if (name.isEmpty()) return ""
            val iterator = BreakIterator.getCharacterInstance()
            iterator.setText(name)
            return name.substring(0, iterator.next())
        }
}
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
    private const val MAX_RESPONSE_BYTES = 1024 * 1024
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
            val json = try {
                val connection =
                    URL("https://chockin-proxy.k0ara.com/api/room").openConnection() as HttpsURLConnection
                val json = try {
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.setRequestProperty("Accept", "application/json")
                    if (connection.responseCode != 200) throw IOException("Unexpected response status")
                    if (connection.contentLengthLong > MAX_RESPONSE_BYTES) throw IOException("Response too large")
                    connection.inputStream.use {
                        val bytes = it.readNBytes(MAX_RESPONSE_BYTES + 1)
                        if (bytes.size > MAX_RESPONSE_BYTES) throw IOException("Response too large")
                        bytes.toString(Charsets.UTF_8)
                    }
                } finally {
                    connection.disconnect()
                }
                parseRoom(json)
                json
            } catch (_: IOException) {
                null
            } catch (_: JSONException) {
                null
            } catch (_: java.time.format.DateTimeParseException) {
                null
            }
            prefs(context).edit {
                if (json != null) putString("json", json)
                putBoolean("failed", json == null)
            }
            json != null
        }
    }
}
