package com.deku.calculator

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)
private val Grey = Color(0xFF8C8C8C)
private val Hairline = Color(0xFF262626)

val Inter = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
)

private enum class KeyKind { Digit, Function, Operator, Equals }

@Immutable
private data class KeySpec(val label: String, val key: Key, val kind: KeyKind)

private fun keypad(clearLabel: String): List<List<KeySpec>> {
    fun d(n: Int) = KeySpec(n.toString(), Key.Digit(n), KeyKind.Digit)
    fun o(op: Op) = KeySpec(op.symbol, Key.Operator(op), KeyKind.Operator)
    return listOf(
        listOf(
            KeySpec(clearLabel, Key.Clear, KeyKind.Function),
            KeySpec("±", Key.Negate, KeyKind.Function),
            KeySpec("%", Key.Percent, KeyKind.Function),
            o(Op.DIV),
        ),
        listOf(d(7), d(8), d(9), o(Op.MUL)),
        listOf(d(4), d(5), d(6), o(Op.SUB)),
        listOf(d(1), d(2), d(3), o(Op.ADD)),
        listOf(
            KeySpec("⌫", Key.Backspace, KeyKind.Function),
            d(0),
            KeySpec(",", Key.Dot, KeyKind.Digit),
            KeySpec("=", Key.Equals, KeyKind.Equals),
        ),
    )
}

@Composable
fun CalculatorScreen(
    state: DisplayState,
    onKey: (Key) -> Unit,
    vanish: Vanish? = null,
    onVanishDone: (Long) -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Black)
            .systemBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Display(state, vanish, onVanishDone, Modifier.weight(1f).fillMaxWidth())
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Spacer(Modifier.height(20.dp))
        Keypad(state, onKey, Modifier.fillMaxWidth().weight(1.55f))
        Spacer(Modifier.height(16.dp))
    }
}

private val ExpressionStyle = TextStyle(
    color = Grey, fontFamily = Inter, fontWeight = FontWeight.Normal,
    fontSize = 20.sp, textAlign = TextAlign.End, fontFeatureSettings = "tnum",
)
private val ResultStyle = TextStyle(
    color = White, fontFamily = Inter, fontWeight = FontWeight.Light,
    textAlign = TextAlign.End, fontFeatureSettings = "tnum",
)
private const val RESULT_MAX_SP = 88f
private const val RESULT_MIN_SP = 30f

// Evaporation timing (ms)
private const val GLYPH_MS = 700f          // each glyph's own animation
private const val MAX_STAGGER_MS = 85f     // delay between neighbouring glyphs
private const val MAX_TOTAL_MS = 1500f     // cap for long numbers (only the stagger shrinks)
private const val EXPRESSION_LEAD_MS = 80f // grey line starts a touch earlier
private const val INCOMING_MS = 300f       // fresh "0" fade-in
private const val INCOMING_OVERLAP_MS = 220f // starts this long before the last glyph finishes

@Composable
private fun Display(state: DisplayState, vanish: Vanish?, onVanishDone: (Long) -> Unit, modifier: Modifier) {
    // One clock drives every glyph and the incoming "0"; all values are read inside graphicsLayer blocks,
    // so each frame is draw-only (no recomposition, no relayout).
    val clock = remember { Animatable(0f) }
    val glyphCount = maxOf(vanish?.result?.length ?: 0, vanish?.expression?.length ?: 0)
    val stagger = if (glyphCount <= 1) 0f
        else minOf(MAX_STAGGER_MS, (MAX_TOTAL_MS - GLYPH_MS) / (glyphCount - 1))
    val resultEnd = GLYPH_MS + stagger * (maxOf(vanish?.result?.length ?: 0, 1) - 1)
    val exprEnd = GLYPH_MS + stagger * (maxOf(vanish?.expression?.length ?: 0, 1) - 1) - EXPRESSION_LEAD_MS
    val glyphsEnd = maxOf(if (vanish?.result != null) resultEnd else 0f, if (vanish?.expression != null) exprEnd else 0f)
    val incomingStart = (glyphsEnd - INCOMING_OVERLAP_MS).coerceAtLeast(0f)
    val endMs = maxOf(glyphsEnd, if (vanish?.result != null) incomingStart + INCOMING_MS else 0f)
    val done by rememberUpdatedState(onVanishDone)

    LaunchedEffect(vanish?.id) {
        val v = vanish ?: return@LaunchedEffect
        clock.snapTo(0f)
        clock.animateTo(endMs, tween(endMs.toInt(), easing = LinearEasing))
        done(v.id)
    }
    val density = LocalDensity.current
    val risePx = with(density) { 8.dp.toPx() }

    Column(
        modifier.padding(bottom = 20.dp, end = 4.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End,
    ) {
        if (vanish?.expression != null) {
            EvaporatingText(vanish.expression, ExpressionStyle, clock, stagger, EXPRESSION_LEAD_MS, "vanish_expression")
        } else {
            BasicText(
                text = state.expression,
                style = ExpressionStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag("expression"),
            )
        }
        Spacer(Modifier.height(4.dp))
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            val measurer = rememberTextMeasurer()
            val maxPx = constraints.maxWidth
            val animating = vanish?.result != null
            val size = remember(state.result, maxPx) { fitSize(measurer, state.result, maxPx) }
            BasicText(
                text = state.result,
                style = ResultStyle.copy(fontSize = size.sp, lineHeight = (size * 1.1f).sp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (animating) Modifier else Modifier.testTag("result"))
                    .graphicsLayer {
                        if (animating) {
                            val t = ((clock.value - incomingStart) / INCOMING_MS).coerceIn(0f, 1f)
                            alpha = Smooth.transform(t)
                            translationY = risePx * (1f - Decelerate.transform(t))
                        } else {
                            alpha = 1f; translationY = 0f
                        }
                    },
            )
            if (animating) {
                val vSize = remember(vanish!!.result, maxPx) { fitSize(measurer, vanish.result!!, maxPx) }
                EvaporatingText(
                    vanish.result!!,
                    ResultStyle.copy(fontSize = vSize.sp, lineHeight = (vSize * 1.1f).sp),
                    clock, stagger, 0f, "vanish_result",
                )
            }
        }
    }
}

/** Width scales linearly with font size: one measurement is enough. */
private fun fitSize(measurer: TextMeasurer, text: String, maxPx: Int): Float {
    val w = measurer.measure(text, ResultStyle.copy(fontSize = RESULT_MAX_SP.sp), maxLines = 1, softWrap = false).size.width
    return if (w <= maxPx || w == 0) RESULT_MAX_SP
    else (RESULT_MAX_SP * maxPx / w * 0.98f).coerceIn(RESULT_MIN_SP, RESULT_MAX_SP)
}

private val Smooth = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)      // gentle ease-in-out (alpha)
private val Decelerate = CubicBezierEasing(0.0f, 0f, 0.2f, 1f)  // smooth decelerate (motion)
private val SoftInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f) // blur ramp

/** Fraction of the glyph's time before it starts to fade (motion leads, alpha lags). */
private const val ALPHA_DELAY = 0.12f
/** Alpha reaches 0 at this fraction of the glyph's time, just before motion ends. */
private const val ALPHA_END = 0.92f

/**
 * Each glyph drifts up (decelerating), then fades with a slight lag, shrinks a few percent and blurs (API 31+).
 * Order: right to left, as if the number is wiped from its last digit.
 */
@Composable
private fun EvaporatingText(
    text: String, style: TextStyle, clock: Animatable<Float, *>, staggerMs: Float, leadMs: Float, tag: String,
) {
    val density = LocalDensity.current
    val driftPx = with(density) { 30.dp.toPx() }
    val blurPx = with(density) { 6.dp.toPx() }
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val n = text.length
    Row(
        Modifier.fillMaxWidth().testTag(tag),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom,
    ) {
        text.forEachIndexed { i, ch ->
            val order = n - 1 - i // rightmost first
            BasicText(
                text = ch.toString(),
                style = style,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    val local = ((clock.value + leadMs - order * staggerMs) / GLYPH_MS).coerceIn(0f, 1f)
                    val motion = Decelerate.transform(local)
                    val fadeT = ((local - ALPHA_DELAY) / (ALPHA_END - ALPHA_DELAY)).coerceIn(0f, 1f)
                    alpha = 1f - Smooth.transform(fadeT)
                    translationY = -driftPx * motion
                    val sc = 1f - 0.07f * motion
                    scaleX = sc; scaleY = sc
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    if (canBlur) {
                        val r = blurPx * SoftInOut.transform(local)
                        renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
                    }
                },
            )
        }
    }
}

@Composable
private fun Keypad(state: DisplayState, onKey: (Key) -> Unit, modifier: Modifier) {
    val rows = remember(state.clearLabel) { keypad(state.clearLabel) }
    val currentOnKey by rememberUpdatedState(onKey)
    BoxWithConstraints(modifier) {
        val gap = 14.dp
        val cellW = maxWidth / 4
        val cellH = maxHeight / rows.size
        val keySize = (min(cellW.value, cellH.value) - gap.value).coerceAtLeast(24f).dp
        // Cells tile the whole keypad with no dead zones: the touch target is the full cell,
        // the circle is only drawn inside it.
        Column(Modifier.fillMaxSize()) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    row.forEach { spec ->
                        CalcKey(
                            spec = spec,
                            circleSize = keySize,
                            selected = spec.key is Key.Operator && spec.key.op == state.activeOp,
                            onKey = currentOnKey,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A key that fires on finger DOWN. Every new pointer that lands on the key fires once,
 * so rapid taps and overlapping multi-finger taps are never lost. No touch slop, no wait for release.
 */
@Composable
private fun CalcKey(spec: KeySpec, circleSize: Dp, selected: Boolean, onKey: (Key) -> Unit, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val view = LocalView.current
    val key by rememberUpdatedState(spec.key)
    val fire by rememberUpdatedState(onKey)

    val (restBg, restFg) = when {
        spec.kind == KeyKind.Equals -> White to Black
        selected -> White to Black
        spec.kind == KeyKind.Function -> Black to Grey
        else -> Black to White
    }
    val pressBg = if (restBg == White) Black else White
    val pressFg = if (restBg == White) White else Black
    // Invert instantly on press, fade back on release. Purely visual; never gates input.
    val bg by animateColorAsState(if (pressed) pressBg else restBg, if (pressed) snap() else tween(140), label = "bg")
    val fg by animateColorAsState(if (pressed) pressFg else restFg, if (pressed) snap() else tween(140), label = "fg")
    val outlined = spec.kind == KeyKind.Operator || spec.kind == KeyKind.Equals
    val fontSize = when {
        spec.label.length > 1 -> (circleSize.value * 0.27f).sp
        spec.kind == KeyKind.Digit -> (circleSize.value * 0.40f).sp
        else -> (circleSize.value * 0.38f).sp
    }

    Box(
        modifier
            .testTag("key_${spec.label}")
            .semantics {
                role = Role.Button
                contentDescription = spec.label
                onClick { fire(key); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    first.consume()
                    pressed = true
                    fire(key)
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        var newDowns = 0
                        event.changes.forEach { c ->
                            if (c.changedToDownIgnoreConsumed()) newDowns++
                            c.consume()
                        }
                        repeat(newDowns) {
                            fire(key)
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(circleSize)
                .clip(CircleShape)
                .background(bg)
                .then(if (outlined) Modifier.border(1.dp, White, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = spec.label,
                style = TextStyle(
                    color = fg,
                    fontFamily = if (spec.label == "⌫") FontFamily.Default else Inter,
                    fontWeight = if (spec.kind == KeyKind.Digit) FontWeight.Light else FontWeight.Normal,
                    fontSize = fontSize,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
