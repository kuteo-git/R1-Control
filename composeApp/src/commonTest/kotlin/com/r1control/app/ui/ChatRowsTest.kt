package com.r1control.app.ui

import com.r1control.app.domain.model.ChatMessage
import com.r1control.app.domain.model.Sender
import com.r1control.app.ui.chat.ChatRow
import com.r1control.app.ui.chat.chatRows
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatRowsTest {
    private val day = 86_400_000L
    private val minute = 60_000L
    private fun dayOf(millis: Long) = millis.floorDiv(day)

    private fun user(text: String, at: Long) = ChatMessage(Sender.User, text, at)
    private fun bot(text: String, at: Long) = ChatMessage(Sender.Assistant, text, at)

    private fun shape(rows: List<ChatRow>) = rows.map {
        when (it) {
            is ChatRow.Day -> "day ${dayOf(it.atMillis)}"
            is ChatRow.Line -> it.message.text + if (it.showsTime) " @" else ""
        }
    }

    /** U-27: yesterday's evening lines sat above today's with nothing between them. */
    @Test fun eachDayGetsAHeadingBeforeItsFirstLine() {
        val rows = chatRows(listOf(bot("bulletin", day + 22 * 60 * minute), user("hi", 2 * day + 3 * minute)), ::dayOf)
        assertEquals(listOf("day 1", "bulletin @", "day 2", "hi @"), shape(rows))
    }

    @Test fun oneTimeUnderTheLastLineOfARunFromOneSenderInOneMinute() {
        val at = 5 * day
        val rows = chatRows(
            listOf(
                bot("a", at), bot("b", at + 10_000), user("c", at + 20_000),
                user("d", at + minute + 1), bot("e", at + minute + 2),
            ),
            ::dayOf,
        )
        assertEquals(listOf("day 5", "a", "b @", "c @", "d @", "e @"), shape(rows))
    }

    @Test fun aLineWithNoTimeGetsNeitherHeadingNorTime() {
        val rows = chatRows(listOf(user("untimed", 0)), ::dayOf)
        assertEquals(listOf("untimed"), shape(rows))
    }

    /** T-19: the R1 keeps 50 lines; when the oldest drops off, every other row must keep its key. */
    @Test fun keysFollowTheMessageNotItsPosition() {
        val at = 3 * day
        val kept = listOf(bot("ok", at + 1), user("cảm ơn", at + 2))
        fun keys(rows: List<ChatRow>) = rows.filterIsInstance<ChatRow.Line>().associate { it.message to it.key }
        val before = keys(chatRows(listOf(user("bật nhạc", at)) + kept, ::dayOf))
        val after = keys(chatRows(kept + bot("new", at + 3), ::dayOf))
        kept.forEach { assertEquals(before[it], after[it], it.text) }
    }

    @Test fun theSameLineTwiceGetsTwoKeys() {
        val line = user("bật nhạc", 3 * day)
        val rows = chatRows(listOf(line, line), ::dayOf)
        assertEquals(rows.size, rows.map { it.key }.toSet().size, "a repeated key is a crash in a lazy list")
    }
}
