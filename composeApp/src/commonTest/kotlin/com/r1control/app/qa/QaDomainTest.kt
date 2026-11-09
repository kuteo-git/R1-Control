package com.r1control.app.qa

import com.r1control.app.domain.model.AddressInput
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.NewsTime
import com.r1control.app.domain.model.PLAY_QUEUE_MAX
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.model.RECENT_SEARCH_CAP
import com.r1control.app.domain.model.RecentSearch
import com.r1control.app.domain.model.VolumeScale
import com.r1control.app.domain.model.cleanKeywords
import com.r1control.app.domain.model.mixInterleaved
import com.r1control.app.domain.model.queueAround
import com.r1control.app.domain.model.rememberSearch
import com.r1control.app.testing.media
import com.r1control.app.testing.track
import com.r1control.app.ui.i18n.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class QaAddressTest {
    private fun valid(text: String) = assertIs<AddressInput.Valid>(R1Address.parse(text), text).address

    @Test fun aCapitalisedSchemeIsStillPlainHttp() {
        assertEquals(R1Address("192.168.1.20", 8088), valid("HTTP://192.168.1.20:8088"))
        assertEquals(R1Address("r1.local", 8088), valid("Http://r1.local"))
    }

    /** `inet_aton`, which iOS resolves through, reads `020` as octal 16: the app would talk to another host. */
    @Test fun leadingZerosInAnIpAreDecimalAndNotPassedOn() {
        assertEquals(R1Address("192.168.1.20", 8088), valid("192.168.001.020"))
    }

    @Test fun spacesInsideTheAddressAreRejectedNotGuessed() {
        for (text in listOf("192.168.1.20 :8088", "192.168. 1.20", "192.168.1.20: 8088")) {
            assertIs<AddressInput.Invalid>(R1Address.parse(text), text)
        }
    }

    @Test fun ipHostnameAndPortFormsAllRead() {
        assertEquals(R1Address("10.25.113.209", 8088), valid("10.25.113.209"))
        assertEquals(R1Address("10.25.113.209", 8088), valid("10.25.113.209:8088"))
        assertEquals(R1Address("10.25.113.209", 9000), valid(" http://10.25.113.209:9000/ "))
        assertEquals(R1Address("phicomm-r1", 8088), valid("phicomm-r1"))
        assertEquals(8088, valid("1.2.3.4:08088").port)
    }

    @Test fun badPortsNameThePort() {
        for (text in listOf("1.2.3.4:", "1.2.3.4:0", "1.2.3.4:99999", "1.2.3.4:8o88")) {
            assertEquals(AddressInput.Reason.BadPort, assertIs<AddressInput.Invalid>(R1Address.parse(text), text).reason, text)
        }
    }
}

class QaQueueWindowTest {
    private fun tracks(n: Int) = (0 until n).map { track("t$it") }
    private fun ids(from: Int, toInclusive: Int) = (from..toInclusive).map { "t$it" }

    @Test fun exactlyTheCapKeepsEverything() {
        val q = queueAround(tracks(PLAY_QUEUE_MAX), 29)
        assertEquals(ids(0, 29), q.tracks.map { it.id })
        assertEquals(29, q.startIndex)
    }

    @Test fun oneOverTheCapAtTheTopEdge() {
        val q = queueAround(tracks(31), 0)
        assertEquals(ids(0, 29), q.tracks.map { it.id })
        assertEquals(0, q.startIndex)
    }

    @Test fun oneOverTheCapAtTheBottomEdge() {
        val q = queueAround(tracks(31), 30)
        assertEquals(ids(1, 30), q.tracks.map { it.id })
        assertEquals(29, q.startIndex)
    }

    @Test fun aLongListTappedNearItsEnd() {
        val q = queueAround(tracks(45), 40)
        assertEquals(ids(15, 44), q.tracks.map { it.id })
        assertEquals(25, q.startIndex)
    }

    @Test fun anIndexOutsideTheListIsClampedIntoIt() {
        assertEquals(0, queueAround(tracks(5), -3).startIndex)
        assertEquals(4, queueAround(tracks(5), 99).startIndex)
        assertEquals(29, queueAround(tracks(45), 99).startIndex)
    }

    /** The property every window must hold, for every list length and every tap. */
    @Test fun theTappedSongIsAlwaysTheOneThatStartsAndTheWindowIsContiguous() {
        for (n in 1..70) {
            val list = tracks(n)
            for (i in 0 until n) {
                val q = queueAround(list, i)
                assertEquals(minOf(n, PLAY_QUEUE_MAX), q.tracks.size, "n=$n i=$i")
                assertEquals(list[i], q.tracks[q.startIndex], "n=$n i=$i")
                val first = list.indexOf(q.tracks.first())
                assertEquals(list.subList(first, first + q.tracks.size), q.tracks, "n=$n i=$i")
            }
        }
    }
}

class QaMixAndRecentsTest {
    @Test fun threeKeywordsWithSongsSharedAcrossThem() {
        val a = listOf(track("1"), track("2"), track("3"))
        val b = listOf(track("2"), track("4"))
        val c = listOf(track("1"), track("5"), track("4"), track("6"))
        assertEquals(listOf("1", "2", "4", "5", "3", "6"), mixInterleaved(listOf(a, b, c)).map { it.id })
    }

    @Test fun noListsAtAllIsNoSongs() {
        assertTrue(mixInterleaved(emptyList()).isEmpty())
    }

    @Test fun blankKeywordsAreNotKeywords() {
        assertTrue(cleanKeywords(listOf("", "   ", "\t", "\n")).isEmpty())
        assertEquals(listOf("Bolero"), cleanKeywords(listOf("Bolero", "bolero ", "  BOLERO")))
        assertEquals(listOf("thanh ngân"), cleanKeywords(listOf("thanh ngân", "thanh   ngân")))
    }

    @Test fun vietnameseKeywordsCompareWithoutCase() {
        val first = rememberSearch(emptyList(), RecentSearch(listOf("Thanh Ngân"), 1))
        val second = rememberSearch(first, RecentSearch(listOf("bolero"), 2))
        val third = rememberSearch(second, RecentSearch(listOf("  THANH NGÂN "), 3))
        assertEquals(listOf(listOf("THANH NGÂN"), listOf("bolero")), third.map { it.keywords })
        assertEquals(3L, third.first().atMillis)
    }

    @Test fun repeatingTheOldestAtTheCapMovesItUpAndDropsNothing() {
        val full = (0 until RECENT_SEARCH_CAP).fold(emptyList<RecentSearch>()) { list, i ->
            rememberSearch(list, RecentSearch(listOf("k$i"), i.toLong()))
        }
        assertEquals(listOf("k0"), full.last().keywords)
        val again = rememberSearch(full, RecentSearch(listOf("k0"), 99))
        assertEquals(RECENT_SEARCH_CAP, again.size)
        assertEquals(listOf("k0"), again.first().keywords)
        assertEquals(full.dropLast(1).map { it.keywords }, again.drop(1).map { it.keywords })
    }

    @Test fun aNewSearchAtTheCapDropsTheOldest() {
        val full = (0 until RECENT_SEARCH_CAP).fold(emptyList<RecentSearch>()) { list, i ->
            rememberSearch(list, RecentSearch(listOf("k$i"), i.toLong()))
        }
        val next = rememberSearch(full, RecentSearch(listOf("new"), 100))
        assertEquals(RECENT_SEARCH_CAP, next.size)
        assertEquals(listOf("new"), next.first().keywords)
        assertTrue(next.none { it.keywords == listOf("k0") })
    }
}

class QaSmallDecisionsTest {
    @Test fun volumeWithinHalfAStepOfAnEdgeGoesToTheEdge() {
        val scale = VolumeScale(15)
        assertEquals(0, scale.snap(0))
        assertEquals(0, scale.snap(3))
        assertEquals(7, scale.snap(4))
        assertEquals(100, scale.snap(97))
        assertEquals(100, scale.snap(100))
        assertEquals(93, scale.snap(96))
    }

    @Test fun aStoppedPlayerWithAKeptQueueHasNoNext() {
        val stopped = media(MediaStatus.Stopped, videoId = "", queue = listOf(track("a"), track("b")))
        assertTrue(!stopped.hasNext)
    }

    @Test fun newsTimeReadsPaddedTextAndRejectsImpossibleTimes() {
        assertEquals(NewsTime(7, 5), NewsTime.parse(" 7:05 "))
        assertEquals(NewsTime.Default, NewsTime.parse("24:00"))
        assertEquals(NewsTime.Default, NewsTime.parse("07:60"))
        assertEquals(NewsTime.Default, NewsTime.parse("7"))
        assertEquals("23:00", NewsTime(0, 0).plusHours(-1).toString())
    }

    @Test fun theLanguageFollowsOnlyThePrimarySubtag() {
        assertEquals(Language.Vietnamese, Language.forTag("vi"))
        assertEquals(Language.Vietnamese, Language.forTag("vi-VN"))
        assertEquals(Language.Vietnamese, Language.forTag("VI"))
        assertEquals(Language.English, Language.forTag("en-VN"))
        assertEquals(Language.English, Language.forTag(""))
    }
}
