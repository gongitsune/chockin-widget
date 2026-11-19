package com.example.chockin_widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.chockin_widget.ui.theme.ChockinWidgetTheme
import com.example.chockin_widget.widget.AppWidget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var refreshing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChockinWidgetTheme {
                val state by remember { RoomRepository.observe(applicationContext) }.collectAsState(
                    RoomState()
                )
                RoomScreen(state, refreshing, ::refresh)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (refreshing) return
        refreshing = true
        lifecycleScope.launch {
            try {
                RoomRepository.refresh(applicationContext)
                AppWidget().updateAll(applicationContext)
            } finally {
                refreshing = false
            }
        }
    }
}

@Composable
internal fun RoomScreen(state: RoomState, refreshing: Boolean, refresh: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val room = state.room
    Scaffold { insets ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(insets), contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 660.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Chockin",
                            Modifier.weight(1f),
                            style = type.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        FilledTonalButton(onClick = refresh, enabled = !refreshing) {
                            Text(if (refreshing) "更新中…" else "更新")
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Surface(shape = RoundedCornerShape(32.dp), color = colors.primaryContainer) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("部室の在室者", style = type.titleMedium)
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        room?.members?.size?.toString() ?: "—",
                                        style = type.displayLarge,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        "人",
                                        Modifier.padding(bottom = 8.dp, start = 4.dp),
                                        style = type.titleLarge
                                    )
                                }
                                if (room != null) Text(
                                    "${room.time} 時点",
                                    style = type.labelMedium
                                )
                            }
                            Image(
                                painterResource(R.drawable.club_room),
                                null,
                                Modifier
                                    .widthIn(max = 112.dp)
                                    .size(96.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text("在室メンバー", style = type.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    if (state.failed) Text(
                        if (room == null) "取得できませんでした。もう一度更新してください。" else "更新できませんでした。前回の情報を表示しています。",
                        Modifier.padding(bottom = 12.dp),
                        color = colors.error,
                        style = type.bodyMedium
                    )
                    if (room?.members?.isEmpty() == true || (room == null && !state.failed)) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = colors.surfaceContainer
                        ) {
                            Text(
                                if (room == null) "読み込み中…" else "現在、部室にいる人はいません",
                                Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp), style = type.bodyLarge
                            )
                        }
                    }
                }
                itemsIndexed(room?.members.orEmpty()) { index, member ->
                    val first = if (index == 0) 24.dp else 6.dp
                    val last = if (index == room!!.members.lastIndex) 24.dp else 6.dp
                    Surface(
                        shape = RoundedCornerShape(first, first, last, last),
                        color = colors.surfaceContainer
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(colors.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    member.name.take(1),
                                    color = colors.onSecondaryContainer,
                                    style = type.titleLarge
                                )
                                AsyncImage(
                                    member.avatar,
                                    null,
                                    Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    member.name,
                                    style = type.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                member.status?.let {
                                    Text(
                                        it,
                                        style = type.bodyMedium,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                }
            }
        }
    }
}
