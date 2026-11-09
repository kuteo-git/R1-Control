package com.r1control.app.ui.i18n

import java.util.Locale

actual fun deviceLanguage(): Language = Language.forTag(Locale.getDefault().language)
