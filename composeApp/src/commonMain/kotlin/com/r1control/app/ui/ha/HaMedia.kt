package com.r1control.app.ui.ha

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.TileId
import com.r1control.app.domain.model.ha.isMediaActive
import com.r1control.app.domain.model.ha.isMediaPlaying
import com.r1control.app.domain.model.ha.isUnavailable
import com.r1control.app.domain.model.ha.mediaArtist
import com.r1control.app.domain.model.ha.mediaCoverUrl
import com.r1control.app.domain.model.ha.mediaDurationS
import com.r1control.app.domain.model.ha.mediaPicture
import com.r1control.app.domain.model.ha.mediaPositionS
import com.r1control.app.domain.model.ha.mediaTitle
import com.r1control.app.domain.model.ha.mediaVolume
import com.r1control.app.domain.model.ha.supportsMediaNext
import com.r1control.app.domain.model.ha.supportsMediaSeek
import com.r1control.app.domain.model.ha.supportsMediaStop
import com.r1control.app.domain.model.ha.supportsMediaVolume
import com.r1control.app.ui.components.CardFrame
import com.r1control.app.ui.components.DiscOverCover
import com.r1control.app.ui.components.IconTap
import com.r1control.app.ui.components.PlayerCard
import com.r1control.app.ui.components.PlayerCardShell
import com.r1control.app.ui.components.PlayerView
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.components.VolumePill
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A Home Assistant media_player drawn with the R1's player card. It has no playlist, since a media_player has
 * no such queue here. Next shows only when the player says it can skip. The position ticks on a timer between
 * Home Assistant's updates, since Home Assistant reports a new one only on a seek or a track change.
 */
@Composable
fun HaMediaCard(tile: HaTile.Media, entity: HaEntity?, actions: HaTileActions, baseUrl: String, modifier: Modifier = Modifier) {
    val name = entity?.friendlyName?.takeIf { it.isNotEmpty() } ?: tile.name
    if (entity == null || entity.isUnavailable || !entity.isMediaActive) {
        HaMediaIdle(name, modifier)
        return
    }

    val duration = entity.mediaDurationS
    val playing = entity.isMediaPlaying
    // Reseeds whenever Home Assistant reports a new volume; the drag writes it locally until released.
    var shownVolume by remember(entity.mediaVolume) { mutableStateOf(entity.mediaVolume) }
    var dragging by remember { mutableStateOf(false) }
    // Reseeds whenever Home Assistant reports a new position or the player's state changes.
    var shown by remember(entity.mediaPositionS, entity.state) { mutableStateOf(entity.mediaPositionS) }
    LaunchedEffect(playing, duration, entity.mediaPositionS, entity.state) {
        while (playing && duration > 0) {
            delay(1000)
            if (!dragging) shown = (shown + 1).coerceAtMost(duration)
        }
    }

    val view = PlayerView(
        title = entity.mediaTitle.ifEmpty { name },
        artist = entity.mediaArtist,
        coverUrl = mediaCoverUrl(baseUrl, entity.mediaPicture),
        positionS = shown,
        durationS = duration,
        playing = playing,
        canSeek = entity.supportsMediaSeek && duration > 0,
        canNext = entity.supportsMediaNext,
        onSpeaker = "",
        preparingPercent = -1,
    )
    val strings = LocalStrings.current
    // The card, then the R1's volume bar under it, the same pill the speaker wears, when the player can be set.
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        PlayerCardShell(view.coverUrl) {
            PlayerCard(
                view = view,
                onPlayPause = { actions.onMedia(tile) },
                onSeek = { dragging = true; shown = it.coerceIn(0, duration) },
                onSeekDone = { actions.onMediaSeek(tile, shown); dragging = false },
                onNext = { actions.onMediaNext(tile) },
                topEnd = {
                    if (entity.supportsMediaStop) {
                        Box(Modifier.clip(CircleShape).background(DiscOverCover)) {
                            IconTap(R1Icons.Stop, strings.stop, { actions.onMediaStop(tile) }, tint = Color.White, iconSize = 20.dp)
                        }
                    }
                },
            )
        }
        if (entity.supportsMediaVolume) {
            VolumePill(
                pct = (shownVolume * 100).roundToInt(),
                steps = 0,
                enabled = true,
                onDrag = { shownVolume = it / 100f },
                onDone = { actions.onMediaVolume(tile, shownVolume) },
            )
        }
    }
}

/** The card for a player that is off or idle: its name, and a line that it is not playing. */
@Composable
private fun HaMediaIdle(name: String, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    PlayerCardShell("", modifier) {
        CardFrame(
            glyph = {
                R1Icon(
                    R1Icons.Play,
                    null,
                    tint = Tokens.OnGround,
                    size = 22.dp,
                    modifier = Modifier.border(2.dp, Tokens.OnGround, CircleShape).padding(3.dp),
                )
            },
            topEnd = {},
            middle = {
                Text(name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = Space.Small))
            },
            bottom = {
                Text(strings.nothingPlaying, style = MaterialTheme.typography.bodyMedium, color = Tokens.OnGroundMuted)
            },
        )
    }
}

// ---- previews ----

@Composable
private fun PreviewHost(strings: com.r1control.app.ui.i18n.Strings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground).padding(Space.Gutter)) { content() } }
    }
}

private val previewTile = HaTile.Media(TileId("m0"), "", "", HaEntityId("media_player.kitchen_audio"))

private val previewPlaying = HaEntity(
    HaEntityId("media_player.kitchen_audio"),
    "playing",
    mapOf(
        "friendly_name" to HaAttribute.Text("Kitchen audio"),
        "media_title" to HaAttribute.Text("Sắc Hoa Màu Nhớ"),
        "media_artist" to HaAttribute.Text("Như Quỳnh, Thế Sơn"),
        "media_position" to HaAttribute.Number(33.0),
        "media_duration" to HaAttribute.Number(303.0),
        "volume_level" to HaAttribute.Number(0.4),
        "supported_features" to HaAttribute.Number(4134.0),
    ),
)

@Preview
@Composable
private fun HaMediaPlayingPreview() = PreviewHost(EnglishStrings) { HaMediaCard(previewTile, previewPlaying, HaTileActions.None, "") }

@Preview
@Composable
private fun HaMediaPlayingVietnamesePreview() = PreviewHost(VietnameseStrings) { HaMediaCard(previewTile, previewPlaying, HaTileActions.None, "") }

@Preview
@Composable
private fun HaMediaNoNextPreview() =
    PreviewHost(EnglishStrings) { HaMediaCard(previewTile, previewPlaying.copy(attributes = previewPlaying.attributes - "supported_features"), HaTileActions.None, "") }

@Preview
@Composable
private fun HaMediaIdlePreview() = PreviewHost(EnglishStrings) { HaMediaCard(previewTile, previewPlaying.copy(state = "off"), HaTileActions.None, "") }
