package com.weatherfocus.app.ui

import com.weatherfocus.app.data.model.ConditionGroup

/** Produces a short "what to wear" hint from the current conditions - not medical/safety advice, just a friendly nudge. */
object ClothingAdvisor {

    fun recommend(tempC: Double?, group: ConditionGroup, windKmh: Double?): String {
        if (tempC == null) return "Check back once today's reading is available."

        val base = when {
            tempC <= 0 -> "Heavy coat, gloves & a hat"
            tempC <= 10 -> "Warm jacket"
            tempC <= 18 -> "Light jacket or a sweater"
            tempC <= 24 -> "T-shirt weather"
            else -> "Shorts & sunscreen"
        }

        val extras = mutableListOf<String>()
        if (group == ConditionGroup.RAIN) extras += "bring an umbrella"
        if (group == ConditionGroup.SNOW) extras += "wear waterproof boots"
        if (group == ConditionGroup.THUNDER) extras += "best to stay indoors if it hits"
        if (windKmh != null && windKmh >= 35) extras += "a windbreaker will help"

        return if (extras.isEmpty()) base else "$base \u2014 ${extras.joinToString(", ")}"
    }
}
