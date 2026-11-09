package com.r1control.app.data

/** A verbatim reply from the R1, read from docs/fixtures (on the test classpath through build.gradle.kts). */
internal fun fixture(name: String): String {
    val stream = checkNotNull(object {}.javaClass.classLoader.getResourceAsStream(name)) { "no fixture $name" }
    return stream.use { it.readBytes().decodeToString() }
}
