package com.r1control.app.ui.i18n

import java.util.Locale

/** The JVM target exists only to run tests; it answers like Android does. */
actual fun deviceLanguage(): Language = Language.forTag(Locale.getDefault().language)
