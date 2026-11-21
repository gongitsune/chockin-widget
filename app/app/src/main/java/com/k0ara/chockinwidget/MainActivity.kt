package com.k0ara.chockinwidget

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.k0ara.chockinwidget.ui.theme.ChockinWidgetTheme
import com.k0ara.chockinwidget.widget.AppWidget
import com.k0ara.chockinwidget.widget.WidgetLocaleWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var refreshing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WidgetLocaleWorker.enqueue(applicationContext)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RoomScreen(state: RoomState, refreshing: Boolean, refresh: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val room = state.room
    val members = room?.members.orEmpty()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pullState = rememberPullToRefreshState()
    var pulled by remember { mutableStateOf(false) }
    val pullRefreshing = pulled && refreshing && room != null
    val snackbar = remember { SnackbarHostState() }
    val refreshFailed = stringResource(R.string.refresh_failed)
    val refreshingDescription = stringResource(R.string.refreshing)
    LaunchedEffect(refreshing) {
        if (!refreshing) {
            pulled = false
            if (state.failed && room != null) snackbar.showSnackbar(refreshFailed)
        }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.brand_name)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = refresh, enabled = !refreshing) {
                        Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.refresh))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { insets ->
        PullToRefreshBox(
            isRefreshing = pullRefreshing,
            onRefresh = { pulled = true; refresh() },
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(insets),
            contentAlignment = Alignment.TopCenter,
            indicator = {
                if (room != null) {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullState,
                        isRefreshing = pullRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter)
                            .semantics { contentDescription = refreshingDescription }
                    )
                }
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 660.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
            ) {
                item {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.primaryContainer) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.room_title), style = type.titleMedium)
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        if (room == null) "—" else members.size.toString(),
                                        style = type.displayLargeEmphasized
                                    )
                                    Text(
                                        pluralStringResource(
                                            R.plurals.people_unit,
                                            members.size
                                        ),
                                        Modifier.padding(bottom = 8.dp, start = 4.dp),
                                        style = type.titleLarge
                                    )
                                }
                                if (room != null) Text(
                                    stringResource(R.string.as_of, room.formattedTime(context)),
                                    style = type.labelMedium
                                )
                            }
                            Image(
                                painterResource(R.drawable.club_room),
                                null,
                                Modifier.size(96.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        stringResource(R.string.present_members),
                        style = type.titleLargeEmphasized
                    )
                    Spacer(Modifier.height(12.dp))
                    when {
                        room == null && state.failed && !refreshing -> Surface(
                            shape = MaterialTheme.shapes.large,
                            color = colors.errorContainer,
                            contentColor = colors.onErrorContainer
                        ) {
                            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                                Text(stringResource(R.string.load_failed), style = type.bodyLarge)
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = refresh,
                                    enabled = !refreshing,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.error,
                                        contentColor = colors.onError
                                    ),
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Text(stringResource(R.string.refresh))
                                }
                            }
                        }
                        room == null -> {
                            val loading = stringResource(R.string.loading)
                            Box(
                                Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ContainedLoadingIndicator(Modifier.semantics { contentDescription = loading })
                            }
                        }
                        members.isEmpty() -> Surface(
                            shape = MaterialTheme.shapes.large,
                            color = colors.surfaceContainer
                        ) {
                            Text(
                                stringResource(R.string.room_empty),
                                Modifier.fillMaxWidth().padding(24.dp),
                                style = type.bodyLarge
                            )
                        }
                    }
                }
                itemsIndexed(members) { index, member ->
                    if (index > 0) Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index, members.size),
                        colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainer),
                        leadingContent = {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(colors.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    member.initial,
                                    color = colors.onPrimaryContainer,
                                    style = type.titleMedium
                                )
                                AsyncImage(
                                    member.avatar,
                                    null,
                                    Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        },
                        supportingContent = member.status?.let { { Text(it) } }
                    ) {
                        Text(member.name)
                    }
                }
            }
        }
    }
}
