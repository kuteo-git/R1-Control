package com.r1control.app.domain

import com.r1control.app.domain.model.BluetoothDevice
import com.r1control.app.domain.model.formatProgress
import com.r1control.app.domain.model.keywordsIn
import com.r1control.app.domain.model.keywordsText
import com.r1control.app.testing.bluetooth
import kotlin.test.Test
import kotlin.test.assertEquals

class KeywordsInTest {
    @Test fun splitsOnCommasAndTrims() {
        assertEquals(listOf("thanh ngân", "sương trắng", "bolero"), keywordsIn("thanh ngân, sương trắng ,bolero"))
    }

    @Test fun theFullWidthCommaOfAVietnameseKeyboardSplitsToo() {
        assertEquals(listOf("a", "b"), keywordsIn("a，b"))
        assertEquals(listOf("a", "b", "c"), keywordsIn("a，b, c"))
    }

    @Test fun blanksAndTrailingCommasAreDropped() {
        assertEquals(listOf("a", "b"), keywordsIn(" a ,, ， b , "))
        assertEquals(emptyList(), keywordsIn(""))
        assertEquals(emptyList(), keywordsIn(" , ，  "))
    }

    @Test fun oneKeywordWithSpacesInsideStaysWhole() {
        assertEquals(listOf("sương trắng miền quê ngoại"), keywordsIn("sương trắng miền quê ngoại"))
    }

    @Test fun aRepeatIsSearchedOnce() {
        assertEquals(listOf("Thành Tâm"), keywordsIn("Thành Tâm,  thành   tâm "))
    }

    @Test fun aRecentReadsBackIntoTheFieldAsTheSameKeywords() {
        val keywords = listOf("a", "b", "c")
        assertEquals("a, b, c", keywordsText(keywords))
        assertEquals(keywords, keywordsIn(keywordsText(keywords)))
    }
}

class ProgressLineTest {
    @Test fun positionOverDuration() {
        assertEquals("1:23 / 4:05", formatProgress(83, 245))
        assertEquals("0:00 / 0:30", formatProgress(0, 30))
    }

    @Test fun anHourGetsHours() {
        assertEquals("1:02:03 / 1:30:00", formatProgress(3723, 5400))
    }

    @Test fun anUnknownLengthShowsOnlyTheClock() {
        assertEquals("0:07", formatProgress(7, 0))
    }
}

class BluetoothPartitionTest {
    private fun dev(
        name: String, bonded: Boolean = true, audio: Boolean = true, streamingIn: Boolean = false, phone: Boolean = false, connected: Boolean = false,
    ) = BluetoothDevice(name, name, bonded, audio, connected, streamingIn, phone)

    @Test fun speakersAreOutputsAndNoDeviceIsBoth() {
        val bt = bluetooth(
            devices = listOf(
                dev("Kitchen"),
                dev("Dongle", streamingIn = true),
                dev("Lan's iPhone", audio = false, phone = true),
                dev("Beacon", bonded = false, audio = false),
                dev("Found speaker", bonded = false),
            ),
        )
        assertEquals(listOf("Kitchen", "Found speaker"), bt.outputDevices.map { it.name })
        assertEquals(listOf("Dongle", "Lan's iPhone"), bt.inputDevices.map { it.name })
    }

    @Test fun aBondedSpeakerThatMisdeclaresItsClassStillShows() {
        val bt = bluetooth(devices = listOf(dev("Odd", audio = false)))
        assertEquals(listOf("Odd"), bt.outputDevices.map { it.name })
    }

    @Test fun aPhoneNobodyPairedWithIsNotListed() {
        val bt = bluetooth(devices = listOf(dev("Stranger", bonded = false, audio = false, phone = true)))
        assertEquals(emptyList(), bt.inputDevices)
        assertEquals(emptyList(), bt.outputDevices)
    }
}
