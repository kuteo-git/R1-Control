package com.r1control.app.domain.model

private val SIZE_PARAMS = Regex("=w\\d+-h\\d+")

/**
 * The R1 reports album art as a 120px thumbnail (`…=w120-h120-l90-rj`), which
 * is a blur on a card the width of the phone. The image host takes the size
 * from that suffix, so asking for [px] is rewriting it. A URL without the
 * suffix is returned as it came.
 */
fun coverAtSize(url: String, px: Int): String = SIZE_PARAMS.replace(url, "=w$px-h$px")
