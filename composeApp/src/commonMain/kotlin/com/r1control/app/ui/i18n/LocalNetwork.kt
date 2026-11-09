package com.r1control.app.ui.i18n

/**
 * Whether this phone asks the user before the app may reach the local network
 * (iOS). A refusal fails every request just as being off the wifi does, so the
 * words for an unreachable speaker name the setting.
 */
expect val phoneAsksForLocalNetwork: Boolean
