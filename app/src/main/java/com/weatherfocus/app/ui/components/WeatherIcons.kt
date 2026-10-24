package com.weatherfocus.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.weatherfocus.app.data.model.ConditionGroup
import kotlin.math.PI
import kotlin.math.sin

/**
 * Bright, animated emoji weather icons. Every condition is one big emoji with its own little motion
 * (spinning sun, bobbing clouds, falling drops and flakes, flashing lightning, drifting fog) so the
 * icons feel alive without needing any image or GIF assets.
 */
@Composable
fun PremiumWeatherIcon(group: ConditionGroup, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "weatherEmoji")
    // 0f -> 1f, loops forever; every periodic motion is derived from this so loops are seamless.
    val loop by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "loop"
    )
    // Slow continuous rotation for the sun.
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label = "spin"
    )
    val wave = sin(loop * 2f * PI.toFloat()) // -1..1
    val density = LocalDensity.current
    val px = with(density) { size.toPx() }
    val bigSp = with(density) { (size * 0.70f).toSp() }
    val smallSp = with(density) { (size * 0.26f).toSp() }

    // Springy "pop in" when the icon first appears or the condition changes.
    val entrance = remember(group) { Animatable(0f) }
    LaunchedEffect(group) {
        entrance.snapTo(0f)
        entrance.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = entrance.value; scaleY = entrance.value },
        contentAlignment = Alignment.Center
    ) {
        when (group) {
            ConditionGroup.CLEAR -> Emoji("☀️", bigSp, Modifier.graphicsLayer {
                rotationZ = spin
                val s = 1f + 0.07f * wave
                scaleX = s; scaleY = s
            })
            ConditionGroup.CLOUDY -> Emoji("⛅", bigSp, Modifier.graphicsLayer {
                translationX = wave * px * 0.06f
                translationY = sin((loop + 0.25f) * 2f * PI.toFloat()) * px * 0.03f
            })
            ConditionGroup.RAIN -> {
                Emoji("🌧️", bigSp, Modifier.graphicsLayer { translationY = -px * 0.06f + wave * px * 0.025f })
                Falling("💧", smallSp, px, loop, xFrac = 0.22f, phase = 0.00f)
                Falling("💧", smallSp, px, loop, xFrac = 0.48f, phase = 0.33f)
                Falling("💧", smallSp, px, loop, xFrac = 0.72f, phase = 0.66f)
            }
            ConditionGroup.SNOW -> {
                Emoji("🌨️", bigSp, Modifier.graphicsLayer { translationY = -px * 0.06f + wave * px * 0.025f })
                Falling("❄️", smallSp, px, loop, xFrac = 0.22f, phase = 0.00f, sway = 0.05f)
                Falling("❄️", smallSp, px, loop, xFrac = 0.48f, phase = 0.40f, sway = 0.05f)
                Falling("❄️", smallSp, px, loop, xFrac = 0.72f, phase = 0.75f, sway = 0.05f)
            }
            ConditionGroup.THUNDER -> {
                Emoji("⛈️", bigSp, Modifier.graphicsLayer {
                    translationX = sin(loop * 2f * PI.toFloat() * 8f) * px * 0.012f
                    translationY = -px * 0.04f
                })
                val flashing = (loop in 0.60f..0.66f) || (loop in 0.72f..0.77f)
                Emoji("⚡", with(density) { (size * 0.42f).toSp() }, Modifier.graphicsLayer {
                    translationX = px * 0.14f
                    translationY = px * 0.26f
                    alpha = if (flashing) 1f else 0f
                    val s = if (flashing) 1.15f else 0.7f
                    scaleX = s; scaleY = s
                })
            }
            ConditionGroup.FOG -> Emoji("🌫️", bigSp, Modifier.graphicsLayer {
                translationX = wave * px * 0.07f
                alpha = 0.78f + 0.22f * wave
            })
            ConditionGroup.UNKNOWN -> Emoji("🌤️", bigSp, Modifier.graphicsLayer {
                translationY = wave * px * 0.04f
            })
        }
    }
}

@Composable
private fun Emoji(text: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = fontSize,
        lineHeight = fontSize * 1.2f,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/** A small emoji that drifts down through the lower half of the icon and fades, looping forever. */
@Composable
private fun Falling(
    emoji: String,
    fontSize: TextUnit,
    px: Float,
    loop: Float,
    xFrac: Float,
    phase: Float,
    sway: Float = 0f
) {
    Box(Modifier.size(with(LocalDensity.current) { px.toDp() })) {
        Emoji(emoji, fontSize, Modifier.graphicsLayer {
            val f = (loop + phase) % 1f
            translationX = px * xFrac - px * 0.06f + sin(f * 2f * PI.toFloat() * 2f) * px * sway
            translationY = px * (0.52f + f * 0.42f)
            alpha = sin(f * PI.toFloat())
        })
    }
}

/** Which small emoji stat icon to show for the "mini stat" rows (rain %, wind, UV, sunrise, sunset, humidity, pressure). */
enum class StatIconType { RAIN, WIND, UV, SUNRISE, SUNSET, HUMIDITY, PRESSURE }

/** Small animated emoji used next to each weather stat, in the same style as [PremiumWeatherIcon]. */
@Composable
fun StatIcon3D(type: StatIconType, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "statEmoji")
    val loop by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "statLoop"
    )
    val wave = sin(loop * 2f * PI.toFloat())
    val density = LocalDensity.current
    val px = with(density) { size.toPx() }
    val sp = with(density) { (size * 0.82f).toSp() }
    val emoji = when (type) {
        StatIconType.RAIN -> "💧"
        StatIconType.WIND -> "💨"
        StatIconType.UV -> "🕶️"
        StatIconType.SUNRISE -> "\u2600\uFE0F"
        StatIconType.SUNSET -> "\uD83C\uDF19"
        StatIconType.HUMIDITY -> "💦"
        StatIconType.PRESSURE -> "🧭"
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Emoji(emoji, sp, Modifier.graphicsLayer {
            when (type) {
                StatIconType.WIND -> translationX = wave * px * 0.12f
                StatIconType.PRESSURE -> rotationZ = wave * 14f
                StatIconType.UV -> { val s = 1f + 0.08f * wave; scaleX = s; scaleY = s }
                else -> translationY = wave * px * 0.07f
            }
        })
    }
}
