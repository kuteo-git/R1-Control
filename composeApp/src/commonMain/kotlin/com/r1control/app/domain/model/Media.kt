package com.r1control.app.domain.model

/**
 * What the speaker's player reports.
 *
 * `Stopped` and `Idle` both mean nothing is playing, whatever queue is kept;
 * [Unknown] is a state this app has no word for, treated the same way.
 */
enum class MediaStatus { Idle, Downloading, Playing, Paused, Stopped, Unknown }

data class MediaState(
    val status: MediaStatus,
    /** Empty when nothing is loaded. */
    val videoId: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val durationS: Int,
    val positionS: Int,
    /** 0..100 while [status] is `Downloading`; meaningless otherwise. */
    val downloadPercent: Int,
    val queue: List<Track>,
) {
    /**
     * Whether the queue holds an item after the current one. A song that is not
     * in the queue (or the last one) has no next: the R1 keeps playing related
     * songs past the queue's end, but the panel offers Next only for a queued
     * one, and so does this app.
     */
    val hasNext: Boolean
        get() {
            val at = queue.indexOfFirst { it.id == videoId }
            return at >= 0 && at < queue.lastIndex
        }
}
