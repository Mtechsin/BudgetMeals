package com.budgetmeals.app.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntSize

object Motion {
    const val FastMs = 150
    const val ShortMs = 150
    const val MediumMs = 250
    const val LongMs = 300

    // Material 3 Emphasized easing curves
    val Emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    val FadeTween: TweenSpec<Float> = tween(ShortMs, easing = EmphasizedDecelerate)
    val SettleBackTween: TweenSpec<Float> = tween(200, easing = EmphasizedDecelerate)
    val ProgressTween: TweenSpec<Float> = tween(LongMs, easing = EmphasizedDecelerate)
    val CardExpandTween: TweenSpec<IntSize> = tween(MediumMs, easing = EmphasizedDecelerate)

    // Bouncy spring for micro-interactions (e.g. checkmarks, tap feedback)
    val MicroSpring: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    // Directional side-by-side continuous slide for bottom navigation tabs.
    // Zero fade, smooth FastOutSlowInEasing curve to eliminate early-frame jerkiness.
    fun tabEnter(forward: Boolean): EnterTransition =
        if (forward) {
            slideInHorizontally(tween(durationMillis = 260, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth }
        } else {
            slideInHorizontally(tween(durationMillis = 260, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth }
        }

    fun tabExit(forward: Boolean): ExitTransition =
        if (forward) {
            slideOutHorizontally(tween(durationMillis = 260, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth }
        } else {
            slideOutHorizontally(tween(durationMillis = 260, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth }
        }

    fun tabSlide(forward: Boolean): ContentTransform =
        tabEnter(forward) togetherWith tabExit(forward)

    val TabEnter: EnterTransition = tabEnter(true)
    val TabExit: ExitTransition = tabExit(true)

    // Full-travel slide for detail screens (100% width)
    val DetailEnterForward: EnterTransition =
        slideInHorizontally(tween(durationMillis = MediumMs, easing = EmphasizedDecelerate)) { fullWidth -> fullWidth }

    val DetailExitForward: ExitTransition =
        fadeOut(tween(durationMillis = ShortMs, easing = EmphasizedAccelerate))

    val DetailEnterBackward: EnterTransition =
        fadeIn(tween(durationMillis = ShortMs, easing = EmphasizedDecelerate))

    val DetailExitBackward: ExitTransition =
        slideOutHorizontally(tween(durationMillis = MediumMs, easing = EmphasizedAccelerate)) { fullWidth -> fullWidth }

    // Smooth transition for content swapping inside a bottom sheet (e.g. QuickAdd -> Form)
    val SheetContentEnter: EnterTransition =
        fadeIn(tween(durationMillis = ShortMs, easing = EmphasizedDecelerate)) +
            scaleIn(tween(durationMillis = ShortMs, easing = EmphasizedDecelerate), initialScale = 0.96f)

    val SheetContentExit: ExitTransition =
        fadeOut(tween(durationMillis = ShortMs, easing = EmphasizedAccelerate)) +
            scaleOut(tween(durationMillis = ShortMs, easing = EmphasizedAccelerate), targetScale = 0.96f)

    // Aliases for compatibility
    val SlideInFullForward: EnterTransition = DetailEnterForward
    val SlideInFullBackward: EnterTransition = DetailEnterBackward
    val DriftExitForward: ExitTransition = DetailExitForward
    val DriftExitBackward: ExitTransition = DetailExitBackward
    val GestureSettleEnter: EnterTransition = fadeIn(FadeTween)
    val GesturePopExit: ExitTransition = ExitTransition.None
}
