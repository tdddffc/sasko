package com.weatherfocus.app.ui

import com.weatherfocus.app.data.model.ConditionGroup
import kotlin.math.roundToInt

/** Small formatting helpers shared by every screen. */
object WeatherFormat {

    fun temp(celsius: Double?, useFahrenheit: Boolean): String {
        if (celsius == null) return "--"
        val value = if (useFahrenheit) celsius * 9 / 5 + 32 else celsius
        return "${value.roundToInt()}\u00B0${if (useFahrenheit) "F" else "C"}"
    }

    fun tempNoUnit(celsius: Double?, useFahrenheit: Boolean): String {
        if (celsius == null) return "--"
        val value = if (useFahrenheit) celsius * 9 / 5 + 32 else celsius
        return "${value.roundToInt()}\u00B0"
    }

    fun emojiFor(group: ConditionGroup): String = when (group) {
        ConditionGroup.CLEAR -> "\u2600\uFE0F"
        ConditionGroup.CLOUDY -> "\u2601\uFE0F"
        ConditionGroup.RAIN -> "\uD83C\uDF27\uFE0F"
        ConditionGroup.SNOW -> "\u2744\uFE0F"
        ConditionGroup.THUNDER -> "\u26C8\uFE0F"
        ConditionGroup.FOG -> "\uD83C\uDF2B\uFE0F"
        ConditionGroup.UNKNOWN -> "\uD83C\uDF24\uFE0F"
    }
}
