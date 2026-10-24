package com.weatherfocus.app.data.repository

import com.weatherfocus.app.data.model.ConditionGroup

/** Maps Open-Meteo's WMO weather codes (https://open-meteo.com/en/docs) to a description + common [ConditionGroup]. */
object WeatherCodeMapper {

    fun describe(code: Int?): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown"
    }

    fun groupOf(code: Int?): ConditionGroup = when (code) {
        0, 1 -> ConditionGroup.CLEAR
        2, 3 -> ConditionGroup.CLOUDY
        45, 48 -> ConditionGroup.FOG
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> ConditionGroup.RAIN
        71, 73, 75, 77, 85, 86 -> ConditionGroup.SNOW
        95, 96, 99 -> ConditionGroup.THUNDER
        else -> ConditionGroup.UNKNOWN
    }

    fun isThunder(code: Int?): Boolean = code != null && code in listOf(95, 96, 99)
    fun isSnow(code: Int?): Boolean = code != null && code in listOf(71, 73, 75, 77, 85, 86)
}

/** Maps free-text descriptions from wttr.in / OpenWeather to the same common [ConditionGroup], for cross-source consensus. */
object TextConditionMapper {
    fun groupOf(description: String?): ConditionGroup {
        val d = description?.lowercase().orEmpty()
        return when {
            d.contains("thunder") -> ConditionGroup.THUNDER
            d.contains("snow") || d.contains("sleet") || d.contains("blizzard") -> ConditionGroup.SNOW
            d.contains("rain") || d.contains("drizzle") || d.contains("shower") -> ConditionGroup.RAIN
            d.contains("fog") || d.contains("mist") || d.contains("haze") -> ConditionGroup.FOG
            d.contains("clear") || d.contains("sunny") -> ConditionGroup.CLEAR
            d.contains("cloud") || d.contains("overcast") -> ConditionGroup.CLOUDY
            else -> ConditionGroup.UNKNOWN
        }
    }

    fun label(group: ConditionGroup): String = when (group) {
        ConditionGroup.CLEAR -> "Clear"
        ConditionGroup.CLOUDY -> "Cloudy"
        ConditionGroup.RAIN -> "Rain"
        ConditionGroup.SNOW -> "Snow"
        ConditionGroup.THUNDER -> "Thunderstorm"
        ConditionGroup.FOG -> "Fog"
        ConditionGroup.UNKNOWN -> "Unknown"
    }
}

/** Maps 7Timer!'s short weather codes (e.g. "clearday", "lightrainnight", "tsday") to the common [ConditionGroup]. */
object SevenTimerConditionMapper {
    fun groupOf(code: String?): ConditionGroup {
        val c = code?.lowercase().orEmpty()
        return when {
            c.startsWith("ts") -> ConditionGroup.THUNDER // tsday / tsnight / tsrainday / tsrainnight
            else -> TextConditionMapper.groupOf(code)
        }
    }
}
