package com.r1control.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.r1control.app.domain.model.formatClock
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.Radius
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens

/**
 * What the player card draws, for any player the app controls: the R1 speaker, or a Home Assistant
 * media_player. Each producer maps its own state to this, so the card branches on nothing but these fields.
 * [onSpeaker] is a line under the title (the R1 names a Bluetooth output there), empty when there is none.
 * [preparingPercent] is 0..100 while the track is being made ready and the bar is a progress, -1 once it
 * plays and the bar is the seek position.
 */
data class PlayerView(
    val title: String,
    val artist: String,
    val coverUrl: String,
    val positionS: Int,
    val durationS: Int,
    val playing: Boolean,
    val canSeek: Boolean,
    val canNext: Boolean,
    val onSpeaker: String,
    val preparingPercent: Int,
) {
    val hasArtist: Boolean get() = artist.isNotEmpty()
    val hasCover: Boolean get() = coverUrl.isNotEmpty()
    val preparing: Boolean get() = preparingPercent >= 0
}

internal val CardMinHeight = 224.dp
internal val DiscOverCover = Color.Black.copy(alpha = 0.45f)

internal val CardGround = Brush.radialGradient(
    colors = listOf(Color(0xFF6B6B71), Color(0xFF454549), Tokens.Card),
    center = Offset(450f, 380f),
    radius = 760f,
)

/**
 * The card's ground: its cover under a dark wash, or the neutral gradient when there is none. [content] draws
 * over it. The R1 speaker draws its own cover for the two sizes its server serves; everything else hands the
 * one url here.
 */
@Composable
fun PlayerCardShell(coverUrl: String, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = CardMinHeight)
            .clip(RoundedCornerShape(Radius.Card))
            .background(Tokens.Card),
    ) {
        Box(Modifier.matchParentSize().background(CardGround))
        if (coverUrl.isNotEmpty()) {
            AsyncImage(coverUrl, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.78f))),
                ),
            )
        }
        content()
    }
}

/**
 * The three-region frame every card body uses: a glyph and a top-end action on top, a middle, and a bottom
 * that sits at the card's foot. It draws no background, so it is placed over a [PlayerCardShell] or the R1's
 * own cover.
 */
@Composable
fun CardFrame(
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

/**
 * The playing body: the title and artist, a play or pause disc, and a position bar that becomes a progress
 * while the track is being prepared. Next shows only when [PlayerView.canNext]; there is no previous, since
 * not every player has one. [topEnd] is the card's extra actions, the R1's queue beside a stop, a media
 * player's stop alone, drawn in the frame's top-end.
 */
@Composable
fun PlayerCard(
    view: PlayerView,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onSeekDone: () -> Unit,
    onNext: () -> Unit,
    topEnd: @Composable () -> Unit,
) {
    val strings = LocalStrings.current
    CardFrame(
        glyph = { R1Icon(R1Icons.MusicNote, null, tint = Tokens.OnGround, size = 26.dp) },
        topEnd = topEnd,
        middle = {
            Row(Modifier.fillMaxWidth().padding(vertical = Space.Small), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = Space.Medium), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
                    Text(view.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (view.hasArtist) {
                        Text(
                            view.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Tokens.OnGround.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (view.onSpeaker.isNotEmpty()) {
                        Text(
                            strings.onSpeaker(view.onSpeaker),
                            style = MaterialTheme.typography.labelMedium,
                            color = Tokens.Accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconTap(
                    icon = if (view.playing) R1Icons.Pause else R1Icons.Play,
                    description = if (view.playing) strings.pause else strings.play,
                    onClick = onPlayPause,
                    enabled = !view.preparing,
                    tint = Color.White,
                    background = DiscOverCover,
                    iconSize = 30.dp,
                    box = 60.dp,
                )
            }
        },
        bottom = {
            if (view.preparing) {
                Column(Modifier.fillMaxWidth().padding(bottom = Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
                    Text(strings.downloading(view.preparingPercent), style = MaterialTheme.typography.labelMedium, color = Tokens.OnGround)
                    LinearProgressIndicator(
                        progress = { view.preparingPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val duration = view.durationS.coerceAtLeast(1)
                        R1Slider(
                            value = view.positionS.coerceIn(0, duration).toFloat(),
                            onValueChange = { onSeek(it.toInt()) },
                            valueRange = 0f..duration.toFloat(),
                            onValueChangeFinished = onSeekDone,
                            enabled = view.canSeek,
                            active = Color.White,
                            inactive = Color.White.copy(alpha = 0.3f),
                            description = strings.seekPosition,
                            valueText = strings.playbackPosition(formatClock(view.positionS), formatClock(view.durationS)),
                        )
                        Row(Modifier.fillMaxWidth().padding(horizontal = Space.Small)) {
                            Text(formatClock(view.positionS), style = MaterialTheme.typography.labelSmall, color = Tokens.OnGround.copy(alpha = 0.8f))
                            Spacer(Modifier.weight(1f))
                            Text(formatClock(view.durationS), style = MaterialTheme.typography.labelSmall, color = Tokens.OnGround.copy(alpha = 0.8f))
                        }
                    }
                    if (view.canNext) {
                        IconTap(R1Icons.Next, strings.next, onNext, tint = Tokens.OnGround)
                    }
                }
            }
        },
    )
}
