package com.r1control.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * There is one Next. `MusicStore.next` decides it (the speaker's own `next`, or
 * a re-send of the edited list), and both cards press it. A second caller of
 * `SpeakerRepository.next` would be a second Next that knows nothing of the
 * phone's lists, and the card that used it would skip songs the owner removed.
 *
 * A source scan, crude on purpose: no file under `commonMain` other than the
 * store and the repository that implements the call may contain `.next(`,
 * except `store.next(`, which is the one Next being pressed.
 */
class OneNextGuardTest {

    private val allowed = setOf("MusicStore.kt", "HttpSpeakerRepository.kt")

    @Test fun onlyTheStoreAsksTheSpeakerForItsNext() {
        val root = sourceRoot().resolve("commonMain/kotlin")
        if (!root.isDirectory) fail("commonMain does not exist; the guard would pass for the wrong reason")
        val problems = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in allowed }
            .flatMap { file -> strayNextCalls(file.readLines()).map { (line, text) -> "${file.name}:$line  $text" } }
            .toList()
        if (problems.isNotEmpty()) {
            fail("A second Next. Press MusicStore.next instead:\n" + problems.joinToString("\n"))
        }
    }

    /** Proves the scanner on known-bad lines, so a guard that matches nothing cannot pass quietly. */
    @Test fun theScannerCatchesEachShapeOfCallItExistsFor() {
        val bad = listOf(
            "    suspend fun next() = command { speaker.next() }",
            "        repository.next()",
            "        r1.next ()",
            "        commandProblem.report(store.next())",
            "// a comment saying speaker.next() is fine",
            "     * so is speaker.next() in a doc",
        )
        assertEquals(listOf(1, 2, 3), strayNextCalls(bad).map { it.first }, "the store's own press and comments are not strays")
    }

    private fun strayNextCalls(lines: List<String>): List<Pair<Int, String>> =
        lines.mapIndexedNotNull { index, line ->
            val t = line.trim()
            val isComment = t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
            if (!isComment && Regex("""(?<!\bstore)\.next\s*\(""").containsMatchIn(line)) (index + 1) to t else null
        }

    private fun sourceRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "composeApp/src")
            if (candidate.isDirectory) return candidate
            val here = File(dir, "src/commonMain")
            if (here.isDirectory) return File(dir, "src")
            dir = dir.parentFile
        }
        fail("could not find composeApp/src from ${File(".").absolutePath}")
    }
}
