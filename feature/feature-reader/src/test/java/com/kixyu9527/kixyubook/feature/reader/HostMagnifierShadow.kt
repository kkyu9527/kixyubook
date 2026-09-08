package com.kixyu9527.kixyubook.feature.reader

import android.widget.Magnifier
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Robolectric has no compositor Surface for the platform magnifier (API 35 dismiss otherwise
 * dereferences a null Surface). Only magnifier pixels are excluded: selection, hit testing,
 * toolbar callbacks and dismissal all use production implementations. Never used in the APK.
 * See https://robolectric.org/extending/ for framework-only custom shadow support.
 */
@Implements(Magnifier::class)
class HostMagnifierShadow {
    @Implementation protected fun show(sourceX: Float, sourceY: Float) = Unit
    @Implementation protected fun show(sourceX: Float, sourceY: Float, magnifierX: Float, magnifierY: Float) = Unit
    @Implementation protected fun update() = Unit
    @Implementation protected fun dismiss() = Unit
}
