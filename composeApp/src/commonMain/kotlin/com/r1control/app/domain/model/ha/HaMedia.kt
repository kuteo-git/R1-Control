package com.r1control.app.domain.model.ha

import kotlin.math.roundToInt

/**
 * What a `media_player` says it is doing and can do, read from its attributes and `supported_features`. The
 * card over it trusts these, so every read of Home Assistant's shape lives here, not in the UI. Positions and
 * durations are whole seconds, as Home Assistant reports and `media_seek` takes.
 */

private const val MEDIA_SEEK = 2
private const val MEDIA_NEXT_TRACK = 32
private const val MEDIA_STOP = 4096

private fun HaEntity.number(key: String): Double? = (attributes[key] as? HaAttribute.Number)?.value
private fun HaEntity.text(key: String): String = (attributes[key] as? HaAttribute.Text)?.value.orEmpty()
private fun HaEntity.features(): Int = number("supported_features")?.toInt() ?: 0

/** The player is playing or paused on something, rather than idle, off or away. */
val HaEntity.isMediaActive: Boolean get() = state == "playing" || state == "paused" || state == "buffering"

val HaEntity.isMediaPlaying: Boolean get() = state == "playing"

/** The track's name, empty when the player names none (the card then shows the player's own name). */
val HaEntity.mediaTitle: String get() = text("media_title")

/** The artist, the album artist as a fallback, empty when neither is given. */
val HaEntity.mediaArtist: String get() = text("media_artist").ifEmpty { text("media_album_artist") }

/** Home Assistant's `entity_picture`, a path on the server (`/api/media_player_proxy/...`), empty when none. */
val HaEntity.mediaPicture: String get() = text("entity_picture")

/** The playhead Home Assistant last reported, in whole seconds; 0 when it reports none. */
val HaEntity.mediaPositionS: Int get() = (number("media_position") ?: 0.0).roundToInt().coerceAtLeast(0)

/** The track's length in whole seconds; 0 when the player reports none (a live stream). */
val HaEntity.mediaDurationS: Int get() = (number("media_duration") ?: 0.0).roundToInt().coerceAtLeast(0)

val HaEntity.supportsMediaNext: Boolean get() = (features() and MEDIA_NEXT_TRACK) != 0
val HaEntity.supportsMediaSeek: Boolean get() = (features() and MEDIA_SEEK) != 0
val HaEntity.supportsMediaStop: Boolean get() = (features() and MEDIA_STOP) != 0

// ---- service calls ----

private fun HaTile.Media.mediaCall(service: String): HaServiceCall = HaServiceCall.on(entity, "media_player", service)

fun HaTile.Media.nextCall(): HaServiceCall = mediaCall("media_next_track")

fun HaTile.Media.stopCall(): HaServiceCall = mediaCall("media_stop")

fun HaTile.Media.seekCall(positionS: Int): HaServiceCall = HaServiceCall(
    "media_player",
    "media_seek",
    mapOf(HaServiceCall.ENTITY_ID to HaAttribute.Text(entity.value), "seek_position" to HaAttribute.Number(positionS.coerceAtLeast(0).toDouble())),
)

// ---- cover url ----

/** The player's address as a base url for its cover: the saved `host:port`, given a scheme when it has none. */
fun haBaseUrl(address: String): String {
    val trimmed = address.trim().trimEnd('/')
    return when {
        trimmed.isEmpty() -> ""
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        else -> "http://$trimmed"
    }
}

/**
 * The full url of a cover from Home Assistant's `entity_picture`. A path joins the base; an already-absolute
 * url (some players give one) is left as it is. Empty in, empty out.
 */
fun mediaCoverUrl(baseUrl: String, picture: String): String = when {
    picture.isEmpty() -> ""
    picture.startsWith("http://") || picture.startsWith("https://") -> picture
    baseUrl.isEmpty() -> ""
    else -> baseUrl.trimEnd('/') + "/" + picture.trimStart('/')
}
