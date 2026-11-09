package com.r1control.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Copy that never reached the dictionary.
 *
 * `Strings` being an interface makes a missing translation impossible: add a
 * property and both languages stop compiling. What no type can see is the
 * literal that was never put on the interface, sitting in a composable and
 * rendering in English on a Vietnamese phone while nothing reports it.
 *
 * Copy is any quoted literal handed to `Text(`, a `contentDescription =`, a
 * glyph's description (`IconTap(icon, "…"`, `R1Icon(icon, "…"`) or a named
 * text parameter (`hint =`, `title =` and the like). Previews are sample data
 * and skipped: a preview runs from its `@Preview` line to the next blank line,
 * and everything under a `// ---- previews ----` line is the file's preview section.
 * Exceptions are listed with a reason, never guessed.
 *
 * A literal built from an expression (`"${'$'}{a} ${'$'}{b}"`) is composed of
 * values that came from elsewhere and is not matched.
 */
class UntranslatedGuardTest {

    private val allowed = mapOf(
        ":" to "the separator between the hours and the minutes of a time",
    )

    @Test fun noScreenShowsAStringThatIsNotInTheDictionary() {
        val ui = sourceRoot().resolve("commonMain/kotlin/com/r1control/app/ui")
        if (!ui.isDirectory) fail("ui does not exist; the guard would pass for the wrong reason")
        val problems = ui.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.contains("/i18n/") }
            .flatMap { file -> untranslatedLiterals(file.readLines()).map { (line, literal) -> "${file.name}:$line untranslated: \"$literal\"" } }
            .toList()
        if (problems.isNotEmpty()) {
            fail(
                "${problems.size} string(s) reach the screen without passing through Strings. " +
                    "Add a property to the interface; both languages then refuse to compile until they have it.\n" +
                    problems.joinToString("\n"),
            )
        }
    }

    /** Proves the scanner on a known-bad source, so a guard that matches nothing cannot pass quietly. */
    @Test fun theScannerCatchesEachShapeOfLiteralItExistsFor() {
        val bad = listOf(
            """Text("Hello")""",
            """Icon(x, contentDescription = "Close")""",
            """IconTap(R1Icons.Close, "Close", onClick)""",
            """R1Icon(R1Icons.Mic, "Mic")""",
            """R1TextField(value, onChange, hint = "Type here")""",
            """ProblemBanner(text = "Oops")""",
        )
        val found = untranslatedLiterals(bad).map { it.second }
        assertEquals(listOf("Hello", "Close", "Close", "Mic", "Type here", "Oops"), found)
    }

    @Test fun theScannerLeavesPreviewsCommentsAndComposedStringsAlone() {
        val fine = listOf(
            "// Text(\"in a comment\")",
            " * Text(\"in a KDoc\")",
            "@Preview",
            "@Composable",
            "private fun P() = Host { Text(\"sample\") }",
            "",
            "Text(strings.search)",
            "Text(\"\${a} \${b}\")",
            "IconTap(R1Icons.Close, strings.close, onClick)",
            "Text(\":\")",
        )
        val section = listOf("// ---- previews ----", "val sample = Track(title = \"Song\")")
        assertEquals(emptyList(), untranslatedLiterals(section))
        assertEquals(emptyList(), untranslatedLiterals(fine))
    }

    private fun untranslatedLiterals(lines: List<String>): List<Pair<Int, String>> {
        val found = mutableListOf<Pair<Int, String>>()
        var inPreview = false
        var previewSection = false
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line == PREVIEW_SECTION) previewSection = true
            if (line.startsWith("@Preview")) inPreview = true
            if (line.isEmpty()) inPreview = false
            if (previewSection || inPreview || isComment(line)) return@forEachIndexed
            COPY_SITE.findAll(line).forEach { match ->
                val literal = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty()
                if (literal.isNotBlank() && literal !in allowed) found += (index + 1) to literal
            }
        }
        return found
    }

    private fun isComment(line: String) = line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")

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

    private companion object {
        const val PREVIEW_SECTION = "// ---- previews ----"
        private val NAMED = "hint|title|subtitle|text|label|description|message|confirmText|cancelText|actionText|buttonText|retryText|placeholder"
        val COPY_SITE = Regex(
            """Text\(\s*"([^"$]*)"""" +
                """|contentDescription\s*=\s*"([^"$]*)"""" +
                """|(?:IconTap|R1Icon)\([^,"]+,\s*"([^"$]*)"""" +
                """|\b(?:$NAMED)\s*=\s*"([^"$]*)"""",
        )
    }
}
