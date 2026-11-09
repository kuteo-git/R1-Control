package com.r1control.app.domain.model

/** A wall-clock time for the bulletin, as the R1 writes it: `HH:mm`. */
data class NewsTime(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23 && minute in 0..59) { "not a time of day: $hour:$minute" }
    }

    fun plusHours(count: Int): NewsTime = NewsTime(((hour + count) % 24 + 24) % 24, minute)

    /** Minutes wrap without carrying into the hour: the two steppers are independent. */
    fun plusMinutes(count: Int): NewsTime = NewsTime(hour, ((minute + count) % 60 + 60) % 60)

    override fun toString(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    companion object {
        val Default = NewsTime(7, 0)

        /** `7:05` and `07:05` both read; anything else is [Default], so a bad value from the R1 still shows a time. */
        fun parse(text: String): NewsTime {
            val parts = text.trim().split(':')
            if (parts.size != 2) return Default
            val h = parts[0].toIntOrNull() ?: return Default
            val m = parts[1].toIntOrNull() ?: return Default
            return if (h in 0..23 && m in 0..59) NewsTime(h, m) else Default
        }
    }
}
