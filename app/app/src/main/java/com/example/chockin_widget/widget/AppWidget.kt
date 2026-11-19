package com.example.chockin_widget.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.example.chockin_widget.MainActivity
import com.example.chockin_widget.RoomRepository
import com.example.chockin_widget.RoomState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

private data class WidgetData(val state: RoomState, val icons: Map<String, Bitmap>)

private suspend fun widgetData(context: Context, state: RoomState): WidgetData =
    withContext(Dispatchers.IO) {
        val urls = state.room?.members.orEmpty().mapNotNull { it.avatar }.distinct()
        val metrics = context.resources.displayMetrics
        val budget = 6.0 * metrics.widthPixels * metrics.heightPixels / urls.size.coerceAtLeast(1)
        val pixels = minOf(36 * metrics.density, sqrt(budget / 4).toFloat()).toInt().coerceIn(1, 96)
        val limit = Semaphore(4)
        val icons = coroutineScope {
            urls.map { url ->
                async {
                    limit.withPermit {
                        val request = ImageRequest.Builder(context).data(url).size(pixels)
                            .transformations(CircleCropTransformation()).allowHardware(false)
                            .build()
                        context.imageLoader.execute(request).drawable?.let {
                            url to it.toBitmap(
                                pixels,
                                pixels
                            )
                        }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        WidgetData(state, icons)
    }

class AppWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val states = RoomRepository.observe(context)
        val initial = widgetData(context, states.first())
        provideContent {
            val data by remember {
                states.map { if (it == initial.state) initial else widgetData(context, it) }
            }.collectAsState(initial)
            GlanceTheme { RoomList(data) }
        }
    }
}

@Composable
private fun RoomList(data: WidgetData) {
    val colors = GlanceTheme.colors
    val room = data.state.room
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp)
            .background(colors.widgetBackground).padding(12.dp)
    ) {
        Row(
            GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Chockin", GlanceModifier.defaultWeight(), style = TextStyle(
                    color = colors.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            if (room != null) Text(
                "${room.members.size} 人",
                GlanceModifier.cornerRadius(16.dp).background(colors.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = TextStyle(color = colors.onPrimaryContainer, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(GlanceModifier.height(10.dp))
        if (data.state.failed) Text(
            if (room == null) "取得できませんでした" else "前回の情報 · ${room.time}",
            style = TextStyle(color = colors.error, fontSize = 12.sp)
        )
        when {
            room == null && !data.state.failed -> Text(
                "読み込み中…",
                style = TextStyle(color = colors.onSurface)
            )

            room == null -> Unit
            room.members.isEmpty() -> Text(
                "現在、在室者はいません",
                style = TextStyle(color = colors.onSurfaceVariant)
            )

            else -> LazyColumn {
                items(room.members.size) { i ->
                    val member = room.members[i]
                    Row(
                        GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).cornerRadius(14.dp)
                            .background(colors.surfaceVariant)
                            .clickable(actionStartActivity<MainActivity>()).padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = data.icons[member.avatar]
                        if (icon != null) Image(
                            ImageProvider(icon),
                            null,
                            GlanceModifier.size(32.dp)
                        )
                        else Box(
                            GlanceModifier.size(32.dp).cornerRadius(16.dp)
                                .background(colors.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                member.name.take(1),
                                style = TextStyle(color = colors.onSecondaryContainer)
                            )
                        }
                        Spacer(GlanceModifier.width(10.dp))
                        Text(
                            member.name,
                            style = TextStyle(
                                color = colors.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
