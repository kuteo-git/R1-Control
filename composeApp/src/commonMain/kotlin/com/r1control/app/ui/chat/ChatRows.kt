package com.r1control.app.ui.chat

import com.r1control.app.domain.model.ChatMessage

/** One row of the conversation as drawn: a day heading or a bubble. [key] is unique in the list and stays with its row. */
sealed interface ChatRow {
    val key: String

    data class Day(val atMillis: Long, override val key: String) : ChatRow

    /** [showsTime] is true on the last line of a run from one sender within one minute. */
    data class Line(val message: ChatMessage, val showsTime: Boolean, override val key: String) : ChatRow
}

/**
 * [messages] (oldest first) as rows: a day heading before the first line of
 * each local day ([dayOf]), and one time per run of lines from the same
 * sender in the same minute. A line with no time gets neither. Keys come from
 * the message itself, not its position, because the R1 keeps only its last 50
 * lines: when the oldest drops off, every index moves and the reader's place
 * must not.
 */
fun chatRows(messages: List<ChatMessage>, dayOf: (Long) -> Long): List<ChatRow> {
    val rows = ArrayList<ChatRow>(messages.size + 4)
    val seen = HashMap<String, Int>()
    var lastDay = Long.MIN_VALUE
    messages.forEachIndexed { index, message ->
        val timed = message.timeMillis > 0
        if (timed) {
            val day = dayOf(message.timeMillis)
            if (day != lastDay) {
                rows += ChatRow.Day(message.timeMillis, "day $day")
                lastDay = day
            }
        }
        val next = messages.getOrNull(index + 1)
        val runGoesOn = next != null && next.timeMillis > 0 && next.sender == message.sender &&
            minuteOf(next.timeMillis) == minuteOf(message.timeMillis) && dayOf(next.timeMillis) == lastDay
        val base = "${message.timeMillis} ${message.sender} ${message.text.hashCode()}"
        val repeat = seen[base] ?: 0
        seen[base] = repeat + 1
        rows += ChatRow.Line(message, showsTime = timed && !runGoesOn, key = "$base #$repeat")
    }
    return rows
}

private fun minuteOf(epochMillis: Long) = epochMillis.floorDiv(60_000L)
