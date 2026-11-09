package com.r1control.app.domain

import com.r1control.app.domain.model.AddressInput
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.model.RecentSearch
import com.r1control.app.domain.model.SavedAddress
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.model.VolumeScale
import com.r1control.app.domain.model.queueAround
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.usecase.CommandOutcome
import com.r1control.app.domain.usecase.PollResult
import com.r1control.app.domain.usecase.SaveAddress
import com.r1control.app.domain.usecase.SaveAddressOutcome
import com.r1control.app.domain.usecase.SearchMusic
import com.r1control.app.domain.usecase.SendChat
import com.r1control.app.domain.usecase.SendOutcome
import com.r1control.app.domain.usecase.SpeakerPoller
import com.r1control.app.domain.usecase.VolumeSetter
import com.r1control.app.domain.usecase.pollMillis
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.testing.MemoryPreferences
import com.r1control.app.testing.StubSpeaker
import com.r1control.app.testing.media
import com.r1control.app.testing.speakerSnapshot
import com.r1control.app.testing.track
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SearchMusicTest {
    private val unreachable = SpeakerException.Unreachable(RuntimeException("down"))

    private fun speaker(answers: Map<String, List<Track>>, failing: Set<String> = emptySet()) = object : StubSpeaker() {
        val asked = mutableListOf<String>()
        override suspend fun search(keyword: String): List<Track> {
            asked += keyword
            if (keyword in failing) throw unreachable
            return answers.getValue(keyword)
        }
    }

    @Test fun searchesEachKeywordAndInterleaves() = runTest {
        val s = speaker(mapOf("a" to listOf(track("a1"), track("a2")), "b" to listOf(track("b1"))))
        val prefs = MemoryPreferences()
        val out = SearchMusic(s, prefs) { 42 }(listOf("a", "b"))
        assertEquals(listOf("a1", "b1", "a2"), out.tracks.map { it.id })
        assertEquals(listOf("a", "b"), out.keywords)
        assertTrue(out.failed.isEmpty())
        assertEquals(listOf(RecentSearch(listOf("a", "b"), 42)), prefs.recents)
    }

    @Test fun blanksAndRepeatsAreNotSearched() = runTest {
        val s = speaker(mapOf("a" to listOf(track("a1"))))
        val out = SearchMusic(s, MemoryPreferences()) { 0 }(listOf(" a ", "", "A", "  "))
        assertEquals(listOf("a"), s.asked)
        assertEquals(listOf("a"), out.keywords)
    }

    @Test fun nothingToSearchMakesNoCallAndRemembersNothing() = runTest {
        val s = speaker(emptyMap())
        val prefs = MemoryPreferences()
        val out = SearchMusic(s, prefs) { 0 }(listOf("", " "))
        assertTrue(s.asked.isEmpty())
        assertTrue(!out.searchedAnything)
        assertTrue(prefs.recents.isEmpty())
    }

    @Test fun oneFailedKeywordDoesNotFailTheOthersAndIsNamed() = runTest {
        val s = speaker(mapOf("a" to listOf(track("a1")), "b" to emptyList()), failing = setOf("b"))
        val prefs = MemoryPreferences()
        val out = SearchMusic(s, prefs) { 7 }(listOf("a", "b"))
        assertEquals(listOf("a1"), out.tracks.map { it.id })
        assertEquals(listOf("b"), out.failed.map { it.keyword })
        assertEquals(unreachable, out.failed.single().error)
        assertTrue(!out.allFailed)
        assertEquals(1, prefs.recents.size)
    }

    @Test fun whenEveryKeywordFailsNothingIsRemembered() = runTest {
        val s = speaker(emptyMap(), failing = setOf("a", "b"))
        val prefs = MemoryPreferences()
        val out = SearchMusic(s, prefs) { 7 }(listOf("a", "b"))
        assertTrue(out.allFailed)
        assertTrue(out.tracks.isEmpty())
        assertTrue(prefs.recents.isEmpty())
    }

    @Test fun aSecondSearchOfTheSameKeywordsReplacesTheFirstInHistory() = runTest {
        val s = speaker(mapOf("a" to listOf(track("a1")), "b" to listOf(track("b1")), "B" to listOf(track("b1"))))
        val prefs = MemoryPreferences()
        val search = SearchMusic(s, prefs) { 1 }
        search(listOf("a", "b"))
        search(listOf("B", "a"))
        assertEquals(listOf(listOf("B", "a")), prefs.recents.map { it.keywords })
    }

    @Test fun aHistoryThatCannotBeWrittenDoesNotLoseTheResults() = runTest {
        val s = speaker(mapOf("a" to listOf(track("a1"))))
        val prefs = object : MemoryPreferences() {
            override suspend fun saveRecentSearches(searches: List<RecentSearch>) = error("disk full")
        }
        val out = SearchMusic(s, prefs) { 0 }(listOf("a"))
        assertEquals(1, out.tracks.size)
    }
}

class PlayAndChatTest {
    private val tracks = (1..40).map { track("t$it") }

    @Test fun windowKeepsAllWhenShort() {
        val q = queueAround(tracks.take(10), 4)
        assertEquals(10, q.tracks.size)
        assertEquals(4, q.startIndex)
    }

    @Test fun windowKeepsTheTappedSongAndThoseAboveItWhenLong() {
        val first = queueAround(tracks, 0)
        assertEquals("t1", first.tracks.first().id)
        assertEquals(30, first.tracks.size)
        assertEquals(0, first.startIndex)

        val mid = queueAround(tracks, 20)
        assertEquals("t11", mid.tracks.first().id)
        assertEquals(10, mid.startIndex)
        assertEquals("t21", mid.tracks[mid.startIndex].id)

        val last = queueAround(tracks, 39)
        assertEquals("t11", last.tracks.first().id)
        assertEquals("t40", last.tracks.last().id)
        assertEquals(29, last.startIndex)
        assertEquals("t40", last.tracks[last.startIndex].id)
    }

    @Test fun chatTrimsAndIgnoresBlank() = runTest {
        val said = mutableListOf<String>()
        val s = object : StubSpeaker() {
            override suspend fun say(text: String) { said += text }
        }
        val chat = SendChat(s)
        assertEquals(SendOutcome.Blank, chat("   "))
        assertEquals(SendOutcome.Sent("xin chào"), chat("  xin chào \n"))
        assertEquals(listOf("xin chào"), said)
    }

    @Test fun chatReportsFailure() = runTest {
        val s = object : StubSpeaker() {
            override suspend fun say(text: String) = throw SpeakerException.NotConfigured()
        }
        assertIs<SendOutcome.Failed>(SendChat(s)("hi"))
    }
}

class SaveAddressTest {
    @Test fun invalidTextIsNeitherProbedNorSaved() = runTest {
        val prefs = MemoryPreferences()
        val out = SaveAddress(StubSpeaker(), prefs)("not an address")
        assertEquals(SaveAddressOutcome.Invalid(AddressInput.Reason.BadHost), out)
        assertEquals(SavedAddress.Unset, prefs.address)
    }

    @Test fun anAddressThatDoesNotAnswerIsNotSaved() = runTest {
        val s = object : StubSpeaker() {
            override suspend fun probe(address: R1Address) = throw SpeakerException.Unreachable(RuntimeException())
        }
        val prefs = MemoryPreferences()
        assertIs<SaveAddressOutcome.Unreachable>(SaveAddress(s, prefs)("192.168.1.20"))
        assertEquals(SavedAddress.Unset, prefs.address)
    }

    @Test fun aReachableAddressIsSavedWithItsDefaultPort() = runTest {
        var probed: R1Address? = null
        val s = object : StubSpeaker() {
            override suspend fun probe(address: R1Address) { probed = address }
        }
        val prefs = MemoryPreferences()
        val out = SaveAddress(s, prefs)("192.168.1.20")
        assertEquals(R1Address("192.168.1.20", 8088), probed)
        assertEquals(SaveAddressOutcome.Saved(R1Address("192.168.1.20", 8088)), out)
        assertEquals(SavedAddress.Set(R1Address("192.168.1.20", 8088)), prefs.address)
    }
}

class SpeakerPollerTest {
    @Test fun cadenceFollowsWhatTheSpeakerIsDoing() {
        assertEquals(0L, pollMillis(null))
        assertEquals(1_000L, pollMillis(PollResult.Fresh(speakerSnapshot(media(MediaStatus.Playing)))))
        assertEquals(1_000L, pollMillis(PollResult.Fresh(speakerSnapshot(media(MediaStatus.Downloading)))))
        assertEquals(3_000L, pollMillis(PollResult.Fresh(speakerSnapshot(media(MediaStatus.Paused)))))
        assertEquals(5_000L, pollMillis(PollResult.Failed(SpeakerException.NotConfigured())))
    }

    @Test fun readsAtTheCadenceAndKeepsGoingThroughAFailure() = runTest {
        var calls = 0
        val s = object : StubSpeaker() {
            override suspend fun snapshot() = when (++calls) {
                1 -> speakerSnapshot(media(MediaStatus.Playing))
                2 -> throw SpeakerException.Unreachable(RuntimeException())
                else -> speakerSnapshot()
            }
        }
        val results = SpeakerPoller(s).snapshots().take(3).toList()
        assertIs<PollResult.Fresh>(results[0])
        assertIs<PollResult.Failed>(results[1])
        assertIs<PollResult.Fresh>(results[2])
        assertEquals(1_000L + 5_000L, currentTime)
    }

    @Test fun stopsReadingWhenNobodyCollects() = runTest {
        var calls = 0
        val s = object : StubSpeaker() {
            override suspend fun snapshot() = speakerSnapshot(media(MediaStatus.Playing)).also { calls++ }
        }
        val collector: Job = launch { SpeakerPoller(s).snapshots().collect { } }
        runCurrent()
        advanceTimeBy(3_500)
        assertEquals(4, calls)
        collector.cancel()
        runCurrent()
        advanceTimeBy(60_000)
        assertEquals(4, calls)
    }

    @Test fun neverReadsTwiceAtOnce() = runTest {
        var inFlight = 0
        var overlapped = false
        val s = object : StubSpeaker() {
            override suspend fun snapshot(): com.r1control.app.domain.model.SpeakerSnapshot {
                if (++inFlight > 1) overlapped = true
                kotlinx.coroutines.delay(2_500)
                inFlight--
                return speakerSnapshot(media(MediaStatus.Playing))
            }
        }
        val collector = launch { SpeakerPoller(s).snapshots().collect { } }
        advanceTimeBy(20_000)
        collector.cancel()
        assertTrue(!overlapped)
    }
}

class VolumeSetterTest {
    private val scale = VolumeScale(15)

    @Test fun throttlesADragAndTheLastValueSentIsTheReleaseValue() = runTest {
        val sent = mutableListOf<Int>()
        val s = object : StubSpeaker() {
            override suspend fun setVolume(pct: Int) { sent += pct }
        }
        val setter = VolumeSetter(backgroundScope, s, intervalMillis = 150) { error("no failure expected") }
        setter.set(10, scale)
        runCurrent()
        for (p in listOf(20, 27, 34, 40, 47)) setter.set(p, scale)
        advanceTimeBy(1_000)
        assertEquals(listOf(13, 47), sent)
        setter.set(47, scale)
        advanceTimeBy(1_000)
        assertEquals(listOf(13, 47), sent, "an unchanged value is not sent again")
    }

    @Test fun valuesAreSnappedToTheScaleBeforeSending() = runTest {
        val sent = mutableListOf<Int>()
        val s = object : StubSpeaker() {
            override suspend fun setVolume(pct: Int) { sent += pct }
        }
        val setter = VolumeSetter(backgroundScope, s) { error("no failure expected") }
        setter.set(39, scale)
        advanceTimeBy(500)
        assertEquals(listOf(40), sent)
    }

    @Test fun aFailedSendIsReportedAndTheSameValueIsTriedAgain() = runTest {
        var attempts = 0
        val failures = mutableListOf<SpeakerException>()
        val s = object : StubSpeaker() {
            override suspend fun setVolume(pct: Int) {
                if (++attempts == 1) throw SpeakerException.Unreachable(RuntimeException())
            }
        }
        val setter = VolumeSetter(backgroundScope, s, intervalMillis = 100) { failures += it }
        setter.set(40, scale)
        advanceTimeBy(300)
        setter.set(40, scale)
        advanceTimeBy(300)
        assertEquals(1, failures.size)
        assertEquals(2, attempts)
    }
}
