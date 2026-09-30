package com.lingualoop.android.ui.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.lingualoop.android.data.api.dto.ExerciseDto
import com.lingualoop.android.ui.theme.ErrorRed
import com.lingualoop.android.ui.theme.SuccessGreen
import com.lingualoop.android.ui.theme.WarnAmber

@Composable
fun LessonScreen(
    lessonId: Long,
    viewModel: LessonViewModel,
    onBackHome: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(lessonId) {
        viewModel.load(lessonId)
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    state.completed?.let { summary ->
        SummaryCard(
            summary = summary,
            offlineNote = state.offlineNote,
            onBackHome = onBackHome,
        )
        return
    }

    val lesson = state.lesson
    if (lesson == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.error ?: "This lesson is unavailable", color = ErrorRed)
        }
        return
    }

    val exercise = state.currentExercise ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(lesson.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            "Exercise ${state.currentIndex + 1} of ${lesson.exercises.size}",
            style = MaterialTheme.typography.bodyMedium,
        )

        state.offlineNote?.let {
            Text(it, color = WarnAmber, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        state.error?.let {
            Text(it, color = ErrorRed, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        }

        ExerciseCard(
            exercise = exercise,
            answerState = state.answers[exercise.id],
            hintAvailable = state.hintAvailable,
            hintRemainingSeconds = state.hintRemainingSeconds,
            localAudio = exercise.audioAsset?.let { state.downloadedAudio[it.id] },
            remoteAudioUrl = exercise.audioAsset?.let { "/api/audio/${it.id}" },
            onEnsureAudio = { exercise.audioAsset?.let { asset -> viewModel.ensureAudio(asset.id) } },
            onAnswer = { text, hintShown -> viewModel.answer(exercise, text, hintShown) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = viewModel::previous, enabled = state.currentIndex > 0) {
                Text("Previous")
            }
            if (state.currentIndex < lesson.exercises.size - 1) {
                Button(onClick = viewModel::next, enabled = state.answers.containsKey(exercise.id)) {
                    Text("Next")
                }
            } else {
                Button(onClick = viewModel::complete, enabled = state.allAnswered) {
                    Text("Complete session")
                }
            }
        }
        if (!state.allAnswered) {
            Text("Answer every exercise to complete the session.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: ExerciseDto,
    answerState: AnswerState?,
    hintAvailable: Boolean,
    hintRemainingSeconds: Int,
    localAudio: java.io.File?,
    remoteAudioUrl: String?,
    onEnsureAudio: () -> Unit,
    onAnswer: (text: String, hintShown: Boolean) -> Unit,
) {
    var answerText by remember(exercise.id) { mutableStateOf("") }
    var hintUsed by remember(exercise.id) { mutableStateOf(false) }
    var captionsVisible by remember(exercise.id) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${exercise.type} · ${exercise.prompt}", style = MaterialTheme.typography.titleMedium)

        when {
            exercise.isMultipleChoice -> {
                exercise.choices.forEach { choice ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = answerText == choice,
                            onClick = { answerText = choice },
                            enabled = answerState == null,
                        )
                        Text(choice)
                    }
                }
                Button(
                    onClick = {
                        hintUsed = false
                        onAnswer(answerText, false)
                    },
                    enabled = answerState == null && answerText.isNotBlank(),
                ) {
                    Text("Check")
                }
            }

            exercise.isListen -> {
                ListenPlayer(
                    assetId = exercise.audioAsset?.id,
                    localAudio = localAudio,
                    remoteAudioUrl = remoteAudioUrl,
                    onEnsureAudio = onEnsureAudio,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { captionsVisible = !captionsVisible; hintUsed = hintUsed || !captionsVisible },
                        enabled = answerState == null && hintAvailable,
                    ) {
                        Text(
                            when {
                                captionsVisible -> "Hide captions"
                                !hintAvailable -> "Captions in ${hintRemainingSeconds}s"
                                else -> "Show captions"
                            }
                        )
                    }
                }
                if (captionsVisible) {
                    Text(exercise.caption.orEmpty())
                }
                OutlinedTextField(
                    value = answerText,
                    onValueChange = { answerText = it },
                    label = { Text("What did you hear?") },
                    enabled = answerState == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onAnswer(answerText, captionsVisible) },
                    enabled = answerState == null && answerText.isNotBlank(),
                ) {
                    Text("Check")
                }
            }

            else -> {
                if (hintAvailable || hintUsed) {
                    OutlinedButton(
                        onClick = { hintUsed = true },
                        enabled = answerState == null,
                    ) {
                        Text("Show hint")
                    }
                } else {
                    Text("Hint in ${hintRemainingSeconds}s", style = MaterialTheme.typography.bodySmall)
                }
                if (hintUsed) {
                    Text(
                        exercise.answer.split(Regex("\\s+"))
                            .joinToString(" ") { word ->
                                word.take(1) + "_".repeat((word.length - 1).coerceAtLeast(0))
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = answerText,
                    onValueChange = { answerText = it },
                    label = { Text("Your translation") },
                    enabled = answerState == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onAnswer(answerText, hintUsed) },
                    enabled = answerState == null && answerText.isNotBlank(),
                ) {
                    Text("Check")
                }
            }
        }

        FeedbackRegion(exercise = exercise, answerState = answerState)
    }
}

@Composable
private fun FeedbackRegion(exercise: ExerciseDto, answerState: AnswerState?) {
    val message = when (answerState?.status) {
        AnswerStatus.SUBMITTING -> "Submitting your answer…"
        AnswerStatus.SUCCESS, AnswerStatus.QUEUED -> {
            if (answerState.correct) "Correct!"
            else "Incorrect — the correct answer is ${exercise.answer}"
        }
        null -> ""
    }
    val queued = answerState?.status == AnswerStatus.QUEUED
    Text(
        text = buildString {
            append(message)
            if (queued) append(" (saved offline, will sync)")
        },
        color = when {
            answerState == null -> MaterialTheme.colorScheme.onSurface
            answerState.correct -> SuccessGreen
            else -> ErrorRed
        },
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = buildString {
                append(message)
                if (queued) append(" Saved offline, will sync later.")
            }
        },
    )
}

@Composable
private fun ListenPlayer(
    assetId: Long?,
    localAudio: java.io.File?,
    remoteAudioUrl: String?,
    onEnsureAudio: () -> Unit,
) {
    if (assetId == null) {
        Text("Audio is not available for this exercise yet.")
        return
    }
    LaunchedEffect(assetId, localAudio) {
        if (localAudio == null) {
            onEnsureAudio()
        }
    }
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LaunchedEffect(localAudio, remoteAudioUrl) {
        val item = when {
            localAudio != null -> MediaItem.fromUri(localAudio.toURI().toString())
            remoteAudioUrl != null -> MediaItem.fromUri("$remoteAudioUrl")
            else -> null
        }
        if (item != null) {
            player.setMediaItem(item)
            player.prepare()
        }
    }
    AndroidView(
        factory = { context ->
            androidx.media3.ui.PlayerView(context).apply { this.player = player }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SummaryCard(
    summary: com.lingualoop.android.data.api.dto.CompleteSessionResponse,
    offlineNote: String?,
    onBackHome: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Session complete", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (offlineNote != null) offlineNote else "Session saved.",
            color = if (offlineNote != null) WarnAmber else SuccessGreen,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text("Exercises attempted: ${summary.attemptCount}")
        Text("Average grade: ${summary.averageGrade?.let { "%.2f".format(it) } ?: "—"} / 5")
        summary.variantKey?.let { Text("Variant: $it") }
        Button(onClick = onBackHome, modifier = Modifier.fillMaxWidth()) {
            Text("Back to home")
        }
    }
}
