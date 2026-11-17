package com.r1control.app.ui.camera

/**
 * Which way up the app may be, read from Swift.
 *
 * On iOS an app cannot rotate itself. The system asks the root view controller,
 * through the app delegate's `application(_:supportedInterfaceOrientationsFor:)`,
 * what it supports and rotates only within that answer. `ComposeUIViewController`
 * is built by the framework and cannot be subclassed from Kotlin to override it,
 * so the answer comes from Swift reading this flag. The decision belongs to the
 * camera, so the flag lives here and the delegate stays a few lines of no state.
 *
 * `Info.plist` lists portrait and both landscapes, the outer bound of what is
 * ever possible, and the delegate narrows it moment to moment. Listing only
 * portrait there would leave this flag unable to do anything.
 */
object OrientationLock {

    /** True only while a camera fills the screen. */
    var landscapeAllowed: Boolean = false
        private set

    fun allow(landscape: Boolean) {
        landscapeAllowed = landscape
    }
}

/** Called from `iOSApp.swift`. A top-level function keeps the object an iOS detail on that side. */
fun landscapeAllowed(): Boolean = OrientationLock.landscapeAllowed
