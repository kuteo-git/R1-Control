package com.r1control.app

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * The dependency arrows, checked by a machine. A crude source scan on purpose:
 * it asks one question per line (does this file name a forbidden package?) and
 * cannot be fooled by cleverness. Lives in jvmTest because it reads files.
 * Imports are matched on code lines only, never on comments.
 */
class ArchitectureGuardTest {

    private val framework = listOf(
        "io.ktor" to "HTTP",
        "kotlinx.serialization" to "a wire format",
        "androidx" to "Android or Compose",
        "android." to "Android",
        "platform." to "an Apple framework",
        "org.jetbrains.compose" to "Compose",
        "com.r1control.app.data" to "the data layer",
        "com.r1control.app.ui" to "the UI layer",
    )

    @Test fun domainKnowsNothingAboutFrameworksOrOuterLayers() {
        assertNoImports("domain", framework)
    }

    @Test fun uiNeverSpeaksHttpOrReachesIntoData() {
        assertNoImports(
            "ui",
            listOf(
                "io.ktor" to "HTTP",
                "kotlinx.serialization" to "a wire format",
                "com.r1control.app.data" to "the data layer",
            ),
        )
    }

    /** Nothing but `data` may carry `@Serializable` or name a serialization import. */
    @Test fun onlyTheDataLayerKnowsAWireFormat() {
        val offenders = sourceFiles().filter { file ->
            !file.path.contains("/com/r1control/app/data/") &&
                codeLines(file).any { it.contains("@Serializable") || it.trim().startsWith("import kotlinx.serialization") }
        }
        if (offenders.isNotEmpty()) {
            fail("@Serializable or kotlinx.serialization outside data:\n" + offenders.joinToString("\n") { it.name })
        }
    }

    /** `/api/state` carries masked secrets and the household's device names: no request logging, ever. */
    @Test fun noRequestLogging() {
        val offenders = sourceFiles().filter { file ->
            codeLines(file).any { it.contains("ktor.client.plugins.logging") || it.contains("install(Logging)") }
        }
        if (offenders.isNotEmpty()) fail("HTTP logging would copy secrets into logs:\n" + offenders.joinToString("\n") { it.name })
    }

    private fun assertNoImports(layer: String, forbidden: List<Pair<String, String>>) {
        val root = sourceRoot().resolve("commonMain/kotlin/com/r1control/app/$layer")
        if (!root.exists()) fail("$layer does not exist; the guard would pass for the wrong reason")
        val problems = buildList {
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("import ")) return@forEachIndexed
                    val import = trimmed.removePrefix("import ")
                    forbidden.firstOrNull { (prefix, _) -> import.startsWith(prefix) }?.let { (_, what) ->
                        add("${file.name}:${index + 1} — $layer imports $what: $import")
                    }
                }
            }
        }
        if (problems.isNotEmpty()) {
            fail("The dependency arrows point the wrong way in ${problems.size} place(s):\n" + problems.joinToString("\n"))
        }
    }

    private fun sourceFiles(): List<File> =
        sourceRoot().resolve("commonMain/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun codeLines(file: File): List<String> = file.readLines().filterNot {
        val t = it.trim()
        t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
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
