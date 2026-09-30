package com.lingualoop.android.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lingualoop.android.data.api.dto.QueueItemDto
import com.lingualoop.android.ui.theme.DeepBlue
import com.lingualoop.android.ui.theme.WarnAmber

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onLogout: () -> Unit,
    onOpenLesson: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("LinguaLoop", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Today's review: ${state.queue?.dueCount ?: 0} due",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(onClick = onLogout) {
                    Icon(
                        Icons.Filled.Logout,
                        contentDescription = "Log out",
                        tint = DeepBlue,
                    )
                }
            }
        }

        if (state.offline) {
            item {
                Text(
                    text = if (state.pendingSync > 0)
                        "Offline — ${state.pendingSync} answer(s) saved on this device and will sync later."
                    else
                        "Offline — showing the last data fetched on this device.",
                    color = WarnAmber,
                    modifier = Modifier.semantics { contentDescription = "Offline notice" },
                )
            }
        }
        state.error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        if (state.loading) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = Color(0xFFE25822),
                    modifier = Modifier.size(40.dp),
                )
                Column {
                    Text(
                        "${state.stats?.streak?.currentDays ?: 0} day streak",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "longest ${state.stats?.streak?.longestDays ?: 0}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.stats?.unit?.let { unit ->
                    MasteryRing(
                        percent = unit.mastery,
                        label = "Mastery of ${unit.title}",
                    )
                }
            }
        }

        state.queue?.items?.forEach { item ->
            item(key = "queue-${item.exerciseId}") {
                QueueRow(item = item, onOpen = { onOpenLesson(item.lessonId) })
            }
        }

        item {
            Button(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun QueueRow(item: QueueItemDto, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text("${item.type} · ${item.prompt}", style = MaterialTheme.typography.titleSmall)
        Text(
            if (item.isNew) "${item.unitTitle} · ${item.lessonTitle} · new"
            else "${item.unitTitle} · ${item.lessonTitle} · reps ${item.review?.repetitions ?: 0}",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onOpen) {
            Text("Review in lesson")
        }
    }
}

@Composable
fun MasteryRing(percent: Double, label: String, modifier: Modifier = Modifier) {
    val clamped = (percent * 100).toInt().coerceIn(0, 100)
    Box(
        modifier = modifier
            .size(84.dp)
            .semantics { contentDescription = "$label: $clamped percent" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 7.dp.toPx())
            drawArc(
                color = Color(0xFFDDE3E9),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = DeepBlue,
                startAngle = -90f,
                sweepAngle = 360f * clamped / 100f,
                useCenter = false,
                style = stroke,
                size = Size(size.width, size.height),
            )
        }
        Text("$clamped%", style = MaterialTheme.typography.titleMedium)
    }
}
