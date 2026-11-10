package com.r1control.app

import com.r1control.app.domain.model.APP_VERSION
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * One release number, five places: the four the stores read and the one
 * Settings shows. 0.1.1 was nearly released saying 0.1.0 in Settings.
 */
class VersionGuardTest {

    @Test fun everyPlaceCarriesTheSameRelease() {
        val repo = repoRoot()
        val gradle = repo.resolve("composeApp/build.gradle.kts").readText()
        val plist = repo.resolve("iosApp/iosApp/Info.plist").readText()
        val versionName = Regex("""versionName = "([^"]+)"""").find(gradle)?.groupValues?.get(1) ?: fail("no versionName")
        val versionCode = Regex("""versionCode = (\d+)""").find(gradle)?.groupValues?.get(1) ?: fail("no versionCode")
        fun plistValue(key: String) =
            Regex("""<key>$key</key>\s*<string>([^<]+)</string>""").find(plist)?.groupValues?.get(1) ?: fail("no $key")

        assertEquals(versionName, APP_VERSION, "Settings shows APP_VERSION")
        assertEquals(versionName, plistValue("CFBundleShortVersionString"))
        assertEquals(versionCode, plistValue("CFBundleVersion"))
        assertTrue(versionCode.toInt() > 0)
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "iosApp/iosApp/Info.plist").isFile) return dir
            dir = dir.parentFile
        }
        fail("could not find the repository root from ${File(".").absolutePath}")
    }
}
