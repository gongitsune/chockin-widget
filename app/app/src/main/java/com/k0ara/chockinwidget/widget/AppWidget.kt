package com.k0ara.chockinwidget.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
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
import com.k0ara.chockinwidget.MainActivity
import com.k0ara.chockinwidget.Member
import com.k0ara.chockinwidget.R
import com.k0ara.chockinwidget.Room
import com.k0ara.chockinwidget.RoomRepository
import com.k0ara.chockinwidget.RoomState
import com.k0ara.chockinwidget.ui.theme.Typography
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
    override val previewSizeMode =
        SizeMode.Responsive(setOf(DpSize(180.dp, 110.dp), DpSize(220.dp, 160.dp)))

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val members = listOf(Member("Linus", null, null), Member("Wozniak", null, null))
        val data = WidgetData(RoomState(Room(members, java.time.Instant.EPOCH)), emptyMap())
        provideContent {
            GlanceTheme { RoomList(data, preview = true) }
        }
    }

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
private fun RoomList(data: WidgetData, preview: Boolean = false) {
    val colors = GlanceTheme.colors
    val room = data.state.room
    val context = LocalContext.current
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .background(colors.widgetBackground)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp)
    ) {
        Row(
            GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                context.getString(R.string.brand_name),
                GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = colors.onSurface,
                    fontSize = Typography.titleLarge.fontSize,
                    fontWeight = FontWeight.Normal
                )
            )
            if (room != null) Text(
                context.resources.getQuantityString(
                    R.plurals.widget_member_count,
                    room.members.size,
                    room.members.size
                ),
                GlanceModifier.cornerRadius(100.dp).background(colors.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = TextStyle(color = colors.onPrimaryContainer, fontSize = Typography.labelLarge.fontSize,
                    fontWeight = FontWeight.Medium)
            )
        }
        Spacer(GlanceModifier.height(10.dp))
        if (data.state.failed) Text(
            if (room == null) context.getString(R.string.widget_load_failed)
            else context.getString(R.string.widget_last_known, room.formattedTime(context)),
            style = TextStyle(color = colors.error, fontSize = Typography.bodySmall.fontSize)
        )
        when {
            room == null && !data.state.failed -> Text(
                context.getString(R.string.loading),
                style = TextStyle(color = colors.onSurface, fontSize = Typography.bodyMedium.fontSize)
            )

            room == null -> Unit
            room.members.isEmpty() -> Text(
                context.getString(R.string.widget_empty),
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = Typography.bodyMedium.fontSize)
            )

            preview -> room.members.forEachIndexed { index, member ->
                MemberRow(member, null, index, room.members.size)
            }
            else -> LazyColumn {
                items(room.members.size) { i ->
                    val member = room.members[i]
                    MemberRow(member, data.icons[member.avatar], i, room.members.size)
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: Member, icon: Bitmap?, index: Int, count: Int) {
    val colors = GlanceTheme.colors
    val background = when {
        count == 1 -> R.drawable.widget_member_single
        index == 0 -> R.drawable.widget_member_first
        index == count - 1 -> R.drawable.widget_member_last
        else -> R.drawable.widget_member_middle
    }
    Column(GlanceModifier.fillMaxWidth()) {
        if (index > 0) Spacer(GlanceModifier.height(ListItemDefaults.SegmentedGap))
        Row(
            GlanceModifier.fillMaxWidth()
                .background(ImageProvider(background), colorFilter = ColorFilter.tint(colors.surface))
                .clickable(actionStartActivity<MainActivity>())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Image(ImageProvider(icon), null, GlanceModifier.size(32.dp))
            } else {
                Box(
                    GlanceModifier.size(32.dp).cornerRadius(16.dp).background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        member.initial,
                        style = TextStyle(
                            color = colors.onPrimaryContainer,
                            fontSize = Typography.titleMedium.fontSize,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
            Spacer(GlanceModifier.width(16.dp))
            Text(
                member.name,
                style = TextStyle(color = colors.onSurface, fontSize = Typography.bodyLarge.fontSize),
                maxLines = 1
            )
        }
    }
}
