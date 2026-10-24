package com.weatherfocus.app.data.model

import com.google.gson.Gson

private val rulesGson = Gson()

/** Parses saved rules; fields missing from older saves keep the data-class defaults (Gson uses the no-arg constructor Kotlin generates). */
fun parseRules(json: String): CustomAlertRules? = runCatching { rulesGson.fromJson(json, CustomAlertRules::class.java) }.getOrNull()

fun rulesToJson(rules: CustomAlertRules): String = rulesGson.toJson(rules)
