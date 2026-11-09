package com.r1control.app.ui.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * The first preferred language, which is what iOS shows the app in;
 * `NSLocale.currentLocale` answers the region format instead.
 */
actual fun deviceLanguage(): Language {
    val tag = NSLocale.preferredLanguages.firstOrNull() as? String ?: return Language.English
    return Language.forTag(tag)
}
