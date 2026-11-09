package com.r1control.app.domain.model

/**
 * The Settings, Language choice. A domain type because it is stored
 * (`PreferencesRepository`) and domain may not import `ui/i18n`'s `Language`;
 * `LanguageChoice.resolve` in `ui/i18n` turns it into one.
 */
enum class LanguageChoice { FollowPhone, Vietnamese, English }
