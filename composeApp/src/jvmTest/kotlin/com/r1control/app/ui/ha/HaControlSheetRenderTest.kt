package com.r1control.app.ui.ha

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.r1control.app.domain.bedroomFan
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.momToiletLight
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.theme.R1Theme
import kotlin.test.Test
import kotlin.test.assertTrue

/** The sheet is measured and drawn for each kind of entity: a crash in measuring, or a sheet with nothing on it, shows only in a render. */
class HaControlSheetRenderTest {
    private fun distinctColours(entity: HaEntity, strings: Strings): Int {
        val density = 3f
        val scene = ImageComposeScene(width = (360 * density).toInt(), height = (640 * density).toInt(), density = Density(density)) {
            CompositionLocalProvider(LocalStrings provides strings) {
                R1Theme {
                    Surface(color = MaterialTheme.colorScheme.background) { HaControlSheet(entity, HaControlActions.None) }
                }
            }
        }
        try {
            val image = scene.render(0)
            val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(image)
            val seen = HashSet<Int>()
            for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) seen += bitmap.getColor(x, y)
            return seen.size
        } finally {
            scene.close()
        }
    }

    @Test fun aDimmableLightDrawsItsSheet() = assertTrue(distinctColours(momToiletLight(128.0, "on"), EnglishStrings) > 4)

    @Test fun aFanDrawsItsSheet() = assertTrue(distinctColours(bedroomFan(state = "on"), EnglishStrings) > 4)

    @Test fun aFanDrawsItsSheetInVietnamese() = assertTrue(distinctColours(bedroomFan(state = "on"), VietnameseStrings) > 4)
}
