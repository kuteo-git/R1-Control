package com.r1control.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Every press is felt. A `clickable` written straight onto a row is a press the
 * phone stays silent for, and nobody notices the one row that is dead to the
 * touch until it is the one they use. So presses go through `tappable`, a
 * switch through `R1Switch` and a slider through `R1Slider`, which play the
 * phone's feedback.
 *
 * A source scan, crude on purpose: under `commonMain/.../ui`, only
 * `Haptics.kt` may call `clickable(`, `selectable(`, `toggleable(` or
 * `combinedClickable(`, and only `Components.kt` may call Material's `Switch(`
 * or `Slider(`. A line that must may say `haptic-guard: <reason>`.
 */
class HapticGuardTest {

    @Test fun everyPressInTheUiIsFelt() {
        val root = sourceRoot().resolve("commonMain/kotlin/com/r1control/app/ui")
        if (!root.isDirectory) fail("$root does not exist; the guard would pass for the wrong reason")
        val problems = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> unfelt(file.name, file.readLines()).map { (line, text) -> "${file.name}:$line  $text" } }
            .toList()
        if (problems.isNotEmpty()) {
            fail("A press the phone does not feel. Use tappable, R1Switch or R1Slider:\n" + problems.joinToString("\n"))
        }
    }

    /** Proves the scanner on known-bad lines, so a guard that matches nothing cannot pass quietly. */
    @Test fun theScannerCatchesEachShapeOfPress() {
        val bad = listOf(
            "            .clickable(role = Role.Button, onClick = onClick)",
            "        Modifier.selectable(selected = on, onClick = onClick)",
            "            .toggleable(value = on, onValueChange = onChange)",
            "            .combinedClickable(onLongClick = {}) {}",
            "    Switch(checked = on, onCheckedChange = onChange)",
            "    androidx.compose.material3.Slider(value = v, onValueChange = {})",
            "            .tappable(role = Role.Button, onClick = onClick)",
            "    R1Switch(on, onChange)",
            "    R1Slider(value = v, onValueChange = {}, valueRange = 0f..1f)",
            "// .clickable( in a comment",
            "            .clickable { } // haptic-guard: a test row",
            "import androidx.compose.foundation.clickable",
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6), unfelt("Screen.kt", bad).map { it.first })
        assertEquals(listOf(5, 6), unfelt("Haptics.kt", bad).map { it.first }, "Haptics.kt owns the press")
        assertEquals(listOf(1, 2, 3, 4), unfelt("Components.kt", bad).map { it.first }, "Components.kt owns the switch and slider")
    }

    private val press = Regex("""\b(clickable|selectable|toggleable|combinedClickable)\s*\(""")
    private val material = Regex("""(?<![\w.])(?:androidx\.compose\.material3\.)?(Switch|Slider)\s*\(""")

    private fun unfelt(fileName: String, lines: List<String>): List<Pair<Int, String>> =
        lines.mapIndexedNotNull { index, line ->
            val t = line.trim()
            val skip = t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") || t.startsWith("import ") || "haptic-guard:" in line
            val code = line.substringBefore("//")
            val stray = (fileName != "Haptics.kt" && press.containsMatchIn(code)) ||
                (fileName != "Components.kt" && material.containsMatchIn(code.replace("androidx.compose.material3.", " ")))
            if (!skip && stray) (index + 1) to t else null
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
