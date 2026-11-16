package com.r1control.app.domain

import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaServiceCall
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.TileId
import com.r1control.app.domain.model.ha.haBaseUrl
import com.r1control.app.domain.model.ha.isMediaActive
import com.r1control.app.domain.model.ha.isMediaPlaying
import com.r1control.app.domain.model.ha.mediaArtist
import com.r1control.app.domain.model.ha.mediaCoverUrl
import com.r1control.app.domain.model.ha.mediaDurationS
import com.r1control.app.domain.model.ha.mediaPicture
import com.r1control.app.domain.model.ha.mediaPositionS
import com.r1control.app.domain.model.ha.mediaTitle
import com.r1control.app.domain.model.ha.nextCall
import com.r1control.app.domain.model.ha.seekCall
import com.r1control.app.domain.model.ha.stopCall
import com.r1control.app.domain.model.ha.supportsMediaNext
import com.r1control.app.domain.model.ha.supportsMediaSeek
import com.r1control.app.domain.model.ha.supportsMediaStop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A speaker as Home Assistant reports it mid-song. Features 4130 = SEEK(2) + NEXT(32) + STOP(4096). */
private fun speaker(state: String = "playing", features: Double = 4130.0) = HaEntity(
    HaEntityId("media_player.kitchen_audio"),
    state,
    mapOf(
        "friendly_name" to HaAttribute.Text("Kitchen audio"),
        "media_title" to HaAttribute.Text("Sắc Hoa Màu Nhớ"),
        "media_artist" to HaAttribute.Text("Như Quỳnh, Thế Sơn"),
        "entity_picture" to HaAttribute.Text("/api/media_player_proxy/media_player.kitchen_audio?token=abc"),
        "media_position" to HaAttribute.Number(33.0),
        "media_duration" to HaAttribute.Number(303.0),
        "supported_features" to HaAttribute.Number(features),
    ),
)

class HaMediaTest {
    @Test fun theActiveStateIsPlayingOrPaused() {
        assertTrue(speaker("playing").isMediaActive)
        assertTrue(speaker("paused").isMediaActive)
        assertFalse(speaker("idle").isMediaActive)
        assertFalse(speaker("off").isMediaActive)
        assertTrue(speaker("playing").isMediaPlaying)
        assertFalse(speaker("paused").isMediaPlaying)
    }

    @Test fun theTrackIsReadFromItsAttributes() {
        val s = speaker()
        assertEquals("Sắc Hoa Màu Nhớ", s.mediaTitle)
        assertEquals("Như Quỳnh, Thế Sơn", s.mediaArtist)
        assertEquals(33, s.mediaPositionS)
        assertEquals(303, s.mediaDurationS)
        assertEquals("/api/media_player_proxy/media_player.kitchen_audio?token=abc", s.mediaPicture)
    }

    @Test fun absentFieldsReadAsEmptyOrZero() {
        val bare = HaEntity(HaEntityId("media_player.x"), "idle", emptyMap())
        assertEquals("", bare.mediaTitle)
        assertEquals("", bare.mediaArtist)
        assertEquals("", bare.mediaPicture)
        assertEquals(0, bare.mediaPositionS)
        assertEquals(0, bare.mediaDurationS)
    }

    @Test fun theArtistFallsBackToTheAlbumArtist() {
        val s = HaEntity(
            HaEntityId("media_player.x"),
            "playing",
            mapOf("media_album_artist" to HaAttribute.Text("V.A.")),
        )
        assertEquals("V.A.", s.mediaArtist)
    }

    @Test fun eachSupportedFeatureBitStandsAlone() {
        assertTrue(speaker().supportsMediaNext)
        assertTrue(speaker().supportsMediaSeek)
        assertTrue(speaker().supportsMediaStop)
        val none = speaker(features = 0.0)
        assertFalse(none.supportsMediaNext)
        assertFalse(none.supportsMediaSeek)
        assertFalse(none.supportsMediaStop)
        assertTrue(speaker(features = 32.0).supportsMediaNext)
        assertFalse(speaker(features = 32.0).supportsMediaSeek)
    }

    @Test fun theMediaCallsNameTheirServices() {
        val tile = HaTile.Media(TileId("t0"), "", "", HaEntityId("media_player.kitchen_audio"))
        assertEquals(HaServiceCall.on(tile.entity, "media_player", "media_next_track"), tile.nextCall())
        assertEquals(HaServiceCall.on(tile.entity, "media_player", "media_stop"), tile.stopCall())
        assertEquals(
            HaServiceCall(
                "media_player",
                "media_seek",
                mapOf(
                    "entity_id" to HaAttribute.Text("media_player.kitchen_audio"),
                    "seek_position" to HaAttribute.Number(90.0),
                ),
            ),
            tile.seekCall(90),
        )
    }

    @Test fun theBaseUrlGetsASchemeWhenItHasNone() {
        assertEquals("http://10.25.113.113:8123", haBaseUrl("10.25.113.113:8123"))
        assertEquals("http://10.25.113.113:8123", haBaseUrl("10.25.113.113:8123/"))
        assertEquals("https://ha.example.com", haBaseUrl("https://ha.example.com"))
        assertEquals("", haBaseUrl(""))
    }

    @Test fun theCoverUrlJoinsTheBaseToThePath() {
        assertEquals(
            "http://10.25.113.113:8123/api/media_player_proxy/x?token=abc",
            mediaCoverUrl("http://10.25.113.113:8123", "/api/media_player_proxy/x?token=abc"),
        )
        assertEquals("https://cdn.example.com/a.jpg", mediaCoverUrl("http://ha", "https://cdn.example.com/a.jpg"))
        assertEquals("", mediaCoverUrl("http://ha", ""))
        assertEquals("", mediaCoverUrl("", "/api/x"))
    }
}
