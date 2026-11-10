package com.r1control.app.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.r1control.app.domain.model.NowPlaying
import com.r1control.app.domain.model.VolumeScale
import com.r1control.app.domain.model.coverAtSize
import com.r1control.app.domain.model.formatClock
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.ui.Problem
import com.r1control.app.ui.components.IconTap
import com.r1control.app.ui.components.ProblemBanner
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.components.R1Slider
import com.r1control.app.ui.components.SwitchRow
import com.r1control.app.ui.components.TonalButton
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.i18n.describe
import com.r1control.app.ui.i18n.phoneAsksForLocalNetwork
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Radius
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens
import kotlin.math.roundToInt

/** What the Control tab can do, so previews pass nothing and the screen passes the view model. */
class ControlActions(
    val onPlayPause: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onSeek: (Int) -> Unit = {},
    val onSeekDone: () -> Unit = {},
    val onVolume: (Int) -> Unit = {},
    val onVolumeDone: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onChangeAddress: () -> Unit = {},
    val onWakeWord: (Boolean) -> Unit = {},
    val onEndSession: () -> Unit = {},
    /** The list of songs playing, on its own page. */
    val onOpenSongs: () -> Unit = {},
)

@Composable
fun ControlScreen(viewModel: ControlViewModel, onChangeAddress: () -> Unit, onOpenSongs: () -> Unit) {
    val state by viewModel.state.collectAsState()
    ControlContent(
        state = state,
        actions = ControlActions(
            onPlayPause = viewModel::onPlayPause,
            onNext = viewModel::onNext,
            onStop = viewModel::onStop,
            onSeek = viewModel::onSeekDrag,
            onSeekDone = viewModel::onSeekRelease,
            onVolume = viewModel::onVolumeDrag,
            onVolumeDone = viewModel::onVolumeRelease,
            onRetry = viewModel::onRetry,
            onChangeAddress = onChangeAddress,
            onWakeWord = viewModel::onWakeWord,
            onEndSession = viewModel::onEndSession,
            onOpenSongs = onOpenSongs,
        ),
    )
}

@Composable
fun ControlContent(state: ControlState, actions: ControlActions, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.Gutter, vertical = Space.Small),
        verticalArrangement = Arrangement.spacedBy(Space.Medium),
    ) {
        val problem = state.problem
        if (problem is Problem.Failed) ProblemBanner(strings.describe(problem.error))
        MediaCard(state.card, state.canNext, actions)
        VolumePill(state.volume, actions.onVolume, actions.onVolumeDone)
        // Under the card, not in it: listening beside a stopped player has a card that shows Nothing.
        VoiceSection(state.voice, actions)
    }
}

/** The listening controls, drawn only for an R1 that has them. */
@Composable
private fun VoiceSection(row: VoiceRow, actions: ControlActions) {
    val strings = LocalStrings.current
    if (row !is VoiceRow.Shown) return
    Column(verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(Tokens.Card).padding(vertical = Space.Small)) {
            SwitchRow(strings.wakeWord, row.wakeWordOn, actions.onWakeWord, subtitle = strings.wakeWordHint)
        }
        when (row.end) {
            EndControl.Hidden -> Unit
            EndControl.Offered -> TonalButton(strings.endConversation, actions.onEndSession, Modifier.fillMaxWidth(), icon = R1Icons.MicOff)
            EndControl.Ending -> TonalButton(strings.endingConversation, {}, Modifier.fillMaxWidth(), enabled = false, icon = R1Icons.MicOff)
        }
    }
}

private val CardMinHeight = 224.dp
private val CardGround = Brush.radialGradient(
    colors = listOf(Color(0xFF6B6B71), Color(0xFF454549), Tokens.Card),
    center = Offset(450f, 380f),
    radius = 760f,
)

@Composable
private fun MediaCard(card: CardState, canNext: Boolean, actions: ControlActions) {
    val music = ((card as? CardState.Showing)?.nowPlaying) as? NowPlaying.Music
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CardMinHeight)
            .clip(RoundedCornerShape(Radius.Card))
            .background(Tokens.Card),
    ) {
        Box(Modifier.matchParentSize().background(CardGround))
        if (music != null && music.hasCover) {
            // The 120 px cover the R1 names is usually cached already (the mini player, the results);
            // it fills the card at once and the 640 px one is drawn over it when it arrives.
            AsyncImage(
                model = music.coverUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            AsyncImage(
                model = coverAtSize(music.coverUrl, 640),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.78f))),
                ),
            )
        }
        when (card) {
            CardState.Connecting -> CardFrame(glyph = {}, topEnd = {}, middle = {}, bottom = {})
            is CardState.Unreachable -> UnreachableBody(card.error, actions)
            is CardState.Showing -> when (val now = card.nowPlaying) {
                NowPlaying.Nothing -> NothingBody()
                is NowPlaying.BluetoothIn -> BluetoothBody(now)
                is NowPlaying.Assistant -> AssistantBody(now)
                is NowPlaying.Music -> MusicBody(now, canNext, actions)
            }
        }
    }
}

@Composable
private fun CardFrame(
    glyph: @Composable () -> Unit,
    topEnd: @Composable () -> Unit,
    middle: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = CardMinHeight).padding(horizontal = Space.Large, vertical = Space.Medium),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            glyph()
            Spacer(Modifier.weight(1f))
            topEnd()
        }
        middle()
        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.BottomCenter) { bottom() }
    }
}

@Composable
private fun NothingBody() {
    val strings = LocalStrings.current
    CardFrame(
        glyph = { R1Icon(R1Icons.Play, null, tint = Tokens.OnGround, size = 22.dp, modifier = Modifier.border(2.dp, Tokens.OnGround, CircleShape).padding(3.dp)) },
        topEnd = {},
        middle = {
            Text(
                strings.nothingPlaying,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = Space.Small),
            )
        },
        bottom = {
            Text(
                strings.nothingPlayingHint,
                style = MaterialTheme.typography.bodyMedium,
                color = Tokens.OnGroundMuted,
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.Small),
            )
        },
    )
}

@Composable
private fun UnreachableBody(error: SpeakerException, actions: ControlActions) {
    val strings = LocalStrings.current
    CardFrame(
        glyph = { R1Icon(R1Icons.Warning, null, tint = Tokens.Error, size = 26.dp) },
        topEnd = {},
        middle = {
            Column(Modifier.padding(vertical = Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
                Text(strings.speakerOffline, style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (error is SpeakerException.Unreachable) strings.speakerOfflineHint(phoneAsksForLocalNetwork) else strings.describe(error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tokens.OnGroundMuted,
                )
            }
        },
        bottom = {
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.Small),
                horizontalArrangement = Arrangement.spacedBy(Space.Small),
            ) {
                TonalButton(strings.tryAgain, actions.onRetry)
                TonalButton(strings.changeAddress, actions.onChangeAddress)
            }
        },
    )
}

@Composable
private fun BluetoothBody(now: NowPlaying.BluetoothIn) {
    val strings = LocalStrings.current
    CardFrame(
        glyph = { R1Icon(R1Icons.Bluetooth, null, tint = Tokens.OnGround, size = 28.dp) },
        topEnd = {},
        middle = {
            Text(
                strings.streamingFrom(now.deviceName),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = Space.Small),
            )
        },
        bottom = {},
    )
}

@Composable
private fun AssistantBody(now: NowPlaying.Assistant) {
    val strings = LocalStrings.current
    CardFrame(
        glyph = { R1Icon(R1Icons.Mic, null, tint = Tokens.OnGround, size = 28.dp) },
        topEnd = {},
        middle = {
            Column(Modifier.padding(vertical = Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
                Text(
                    when (now.activity) {
                        NowPlaying.Activity.Listening -> strings.assistantListening
                        NowPlaying.Activity.Speaking -> strings.assistantSpeaking
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (now.hasLastLine) {
                    Text(
                        now.lastLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Tokens.OnGroundMuted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        bottom = {},
    )
}

private val DiscOverCover = Color.Black.copy(alpha = 0.45f)

@Composable
private fun MusicBody(now: NowPlaying.Music, canNext: Boolean, actions: ControlActions) {
    val strings = LocalStrings.current
    CardFrame(
        glyph = { R1Icon(R1Icons.MusicNote, null, tint = Tokens.OnGround, size = 26.dp) },
        topEnd = {
            // One capsule for the two secondary actions, so only Pause reads as a disc.
            Row(Modifier.clip(CircleShape).background(DiscOverCover), verticalAlignment = Alignment.CenterVertically) {
                IconTap(R1Icons.Queue, strings.songsPlaying, actions.onOpenSongs, tint = Color.White, iconSize = 20.dp)
                Box(Modifier.width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.22f)))
                IconTap(R1Icons.Stop, strings.stop, actions.onStop, tint = Color.White, iconSize = 20.dp)
            }
        },
        middle = {
            Row(Modifier.fillMaxWidth().padding(vertical = Space.Small), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = Space.Medium), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
                    Text(
                        now.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (now.hasArtist) {
                        Text(
                            now.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Tokens.OnGround.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val output = now.output
                    if (output is NowPlaying.Output.Bluetooth) {
                        Text(
                            strings.onSpeaker(output.speakerName),
                            style = MaterialTheme.typography.labelMedium,
                            color = Tokens.Accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val phase = now.phase
                IconTap(
                    icon = if (phase == NowPlaying.Phase.Playing) R1Icons.Pause else R1Icons.Play,
                    description = if (phase == NowPlaying.Phase.Playing) strings.pause else strings.play,
                    onClick = actions.onPlayPause,
                    enabled = phase !is NowPlaying.Phase.Downloading,
                    tint = Color.White,
                    background = DiscOverCover,
                    iconSize = 30.dp,
                    box = 60.dp,
                )
            }
        },
        bottom = {
            val phase = now.phase
            if (phase is NowPlaying.Phase.Downloading) {
                Column(Modifier.fillMaxWidth().padding(bottom = Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    Text(strings.downloading(phase.percent), style = MaterialTheme.typography.labelMedium, color = Tokens.OnGround)
                    LinearProgressIndicator(
                        progress = { phase.percent / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                    )
                }
            } else {
                // No previous: the R1 has no such command. Next only when the store says there is one to go to.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val duration = now.durationS.coerceAtLeast(1)
                        R1Slider(
                            value = now.positionS.coerceIn(0, duration).toFloat(),
                            onValueChange = { actions.onSeek(it.toInt()) },
                            valueRange = 0f..duration.toFloat(),
                            onValueChangeFinished = actions.onSeekDone,
                            enabled = now.canSeek,
                            active = Color.White,
                            inactive = Color.White.copy(alpha = 0.3f),
                            description = strings.seekPosition,
                            valueText = strings.playbackPosition(formatClock(now.positionS), formatClock(now.durationS)),
                        )
                        Row(Modifier.fillMaxWidth().padding(horizontal = Space.Small)) {
                            Text(formatClock(now.positionS), style = MaterialTheme.typography.labelSmall, color = Tokens.OnGround.copy(alpha = 0.8f))
                            Spacer(Modifier.weight(1f))
                            Text(formatClock(now.durationS), style = MaterialTheme.typography.labelSmall, color = Tokens.OnGround.copy(alpha = 0.8f))
                        }
                    }
                    if (canNext) {
                        IconTap(R1Icons.Next, strings.next, actions.onNext, tint = Tokens.OnGround)
                    }
                }
            }
        },
    )
}

/**
 * The volume bar: the fill is the level, and a horizontal drag anywhere moves
 * it by how far the finger travels (a tap writes nothing: at night an
 * accidental touch must not jump the speaker to 100%). The shown number is the
 * finger's, not the poll's. For a screen reader it is one adjustable element
 * that steps through the speaker's hardware steps.
 */
@Composable
fun VolumePill(volume: VolumeState, onDrag: (Int) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val level by rememberUpdatedState(volume.pct)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(CircleShape)
            .background(Tokens.Card)
            .clearAndSetSemantics {
                contentDescription = strings.volume
                if (volume.enabled) {
                    stateDescription = strings.volumePercent(volume.pct)
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = volume.pct.toFloat(),
                        range = 0f..100f,
                        steps = (volume.scale.steps - 1).coerceAtLeast(0),
                    )
                    setProgress { target ->
                        onDrag(target.roundToInt())
                        onDone()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(volume.enabled) {
                if (!volume.enabled) return@pointerInput
                var start = 0
                var travelled = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        start = level
                        travelled = 0f
                    },
                    onDragEnd = onDone,
                    onDragCancel = onDone,
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        travelled += dx
                        onDrag(volumeAfterDrag(start, travelled, size.width.toFloat()))
                    },
                )
            },
    ) {
        if (volume.enabled) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(volume.pct / 100f).background(Tokens.VolumeFill))
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = Space.XLarge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            R1Icon(if (volume.enabled && volume.pct == 0) R1Icons.VolumeOff else R1Icons.Volume, null, tint = Tokens.Accent, size = 26.dp)
            if (volume.enabled) {
                Spacer(Modifier.width(Space.Large))
                Text(strings.volumePercent(volume.pct), style = MaterialTheme.typography.bodyLarge, color = Tokens.Accent)
            }
        }
    }
}

// ---- previews ----

private val playing = NowPlaying.Music(
    title = "Thành Tâm Sám Hối", artist = "NSƯT Thanh Ngân", coverUrl = "", positionS = 15, durationS = 604,
    phase = NowPlaying.Phase.Playing, output = NowPlaying.Output.ThisSpeaker,
)
private val volume = VolumeState(40, VolumeScale(15), enabled = true)

@Composable
private fun PreviewHost(strings: Strings, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground)) { content() } }
    }
}

private fun controlState(
    now: NowPlaying,
    canNext: Boolean = true,
    voice: VoiceRow = VoiceRow.Hidden,
    volume: VolumeState = com.r1control.app.ui.control.volume,
) = ControlState(CardState.Showing(now), volume, Problem.None, canNext, voice)

@Preview
@Composable
private fun ControlConnectingPreview() = PreviewHost(EnglishStrings) {
    ControlContent(ControlState.Initial, ControlActions())
}

@Preview
@Composable
private fun ControlIdlePreview() = PreviewHost(EnglishStrings) {
    ControlContent(controlState(NowPlaying.Nothing), ControlActions())
}

@Preview
@Composable
private fun ControlPlayingPreview() = PreviewHost(EnglishStrings) {
    ControlContent(controlState(playing), ControlActions())
}

@Preview
@Composable
private fun ControlDownloadingBluetoothPreview() = PreviewHost(EnglishStrings) {
    val now = playing.copy(phase = NowPlaying.Phase.Downloading(40), output = NowPlaying.Output.Bluetooth("Kitchen speaker"))
    ControlContent(controlState(now), ControlActions())
}

@Preview
@Composable
private fun ControlAssistantPreview() = PreviewHost(EnglishStrings) {
    val now = NowPlaying.Assistant(NowPlaying.Activity.Speaking, "Hôm nay trời nắng đẹp, nhiệt độ khoảng ba mươi độ.")
    ControlContent(controlState(now), ControlActions())
}

@Preview
@Composable
private fun ControlFailedPreview() = PreviewHost(EnglishStrings) {
    ControlContent(
        ControlState.Initial.copy(problem = Problem.Failed(SpeakerException.Unreachable(RuntimeException("timeout")))),
        ControlActions(),
    )
}

@Preview
@Composable
private fun ControlVietnamesePreview() = PreviewHost(VietnameseStrings) {
    ControlContent(controlState(playing), ControlActions())
}

@Preview
@Composable
private fun ControlUnreachablePreview() = PreviewHost(EnglishStrings) {
    ControlContent(ControlState.Initial.copy(card = CardState.Unreachable(SpeakerException.Unreachable(RuntimeException("x")))), ControlActions())
}

@Preview
@Composable
private fun ControlUnreachableVietnamesePreview() = PreviewHost(VietnameseStrings) {
    ControlContent(ControlState.Initial.copy(card = CardState.Unreachable(SpeakerException.Unreachable(RuntimeException("x")))), ControlActions())
}

@Preview
@Composable
private fun ControlMutedPreview() = PreviewHost(VietnameseStrings) {
    ControlContent(controlState(NowPlaying.Nothing, volume = volume.copy(pct = 0)), ControlActions())
}

@Preview
@Composable
private fun ControlAssistantWithVoiceControlsPreview() = PreviewHost(EnglishStrings) {
    val now = NowPlaying.Assistant(NowPlaying.Activity.Listening, "")
    ControlContent(controlState(now, voice = VoiceRow.Shown(wakeWordOn = true, end = EndControl.Offered)), ControlActions())
}

@Preview
@Composable
private fun ControlListeningBesideStoppedWakeOffPreview() = PreviewHost(EnglishStrings) {
    ControlContent(controlState(NowPlaying.Nothing, voice = VoiceRow.Shown(wakeWordOn = false, end = EndControl.Offered)), ControlActions())
}

@Preview
@Composable
private fun ControlMusicNoEndPreview() = PreviewHost(EnglishStrings) {
    ControlContent(controlState(playing, voice = VoiceRow.Shown(wakeWordOn = true, end = EndControl.Hidden)), ControlActions())
}

@Preview
@Composable
private fun ControlLastSongNoNextPreview() = PreviewHost(EnglishStrings) {
    ControlContent(controlState(playing, canNext = false), ControlActions())
}

@Preview
@Composable
private fun ControlVoiceControlsVietnamesePreview() = PreviewHost(VietnameseStrings) {
    val now = NowPlaying.Assistant(NowPlaying.Activity.Speaking, "Hôm nay trời nắng đẹp.")
    ControlContent(controlState(now, voice = VoiceRow.Shown(wakeWordOn = false, end = EndControl.Offered)), ControlActions())
}

@Preview
@Composable
private fun ControlEndingVietnamesePreview() = PreviewHost(VietnameseStrings) {
    val now = NowPlaying.Assistant(NowPlaying.Activity.Listening, "")
    ControlContent(controlState(now, voice = VoiceRow.Shown(wakeWordOn = true, end = EndControl.Ending)), ControlActions())
}
