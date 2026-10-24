package com.weatherfocus.app

import com.google.gson.JsonParser
import com.weatherfocus.app.data.alert.CustomWeatherAlertEngine
import com.weatherfocus.app.data.model.AlertRuleType
import com.weatherfocus.app.data.model.AlertSeverity
import com.weatherfocus.app.data.model.CustomAlertRules
import com.weatherfocus.app.data.model.FloodContext
import com.weatherfocus.app.data.model.MetNoData
import com.weatherfocus.app.data.model.MetNoDetails
import com.weatherfocus.app.data.model.MetNoInstant
import com.weatherfocus.app.data.model.MetNoNextHours
import com.weatherfocus.app.data.model.MetNoPeriodDetails
import com.weatherfocus.app.data.model.MetNoProperties
import com.weatherfocus.app.data.model.MetNoResponse
import com.weatherfocus.app.data.model.MetNoSummary
import com.weatherfocus.app.data.model.MetNoTimeseries
import com.weatherfocus.app.data.model.OpenMeteoHourly
import com.weatherfocus.app.data.model.parseRules
import com.weatherfocus.app.data.model.rulesToJson
import com.weatherfocus.app.data.repository.YrMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AlertEngineTest {

    private val keyFmt get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)

    /** "Today 20:00" in the JVM's time zone: evening, so the car alerts for the coming morning are due. */
    private fun evening(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun hourly(
        now: Long, hours: Int = 48,
        temp: (Int) -> Double = { 5.0 },
        rain: (Int) -> Double = { 0.0 },
        code: (Int) -> Int = { 3 },
        pop: (Int) -> Int = { 0 },
        wind: (Int) -> Double = { 5.0 },
        cloud: (Int) -> Int = { 50 }
    ): OpenMeteoHourly {
        val fmt = keyFmt
        val r = (0 until hours)
        return OpenMeteoHourly(
            time = r.map { fmt.format(Date(now + it * 3_600_000L)) },
            temperature_2m = r.map { temp(it) },
            precipitation_probability = r.map { pop(it) },
            weather_code = r.map { code(it) },
            wind_speed_10m = r.map { wind(it) },
            uv_index = r.map { 0.0 },
            cloud_cover = r.map { cloud(it) },
            wind_direction_10m = r.map { 270 },
            dew_point_2m = r.map { temp(it) - 6 },
            apparent_temperature = r.map { temp(it) },
            relative_humidity_2m = r.map { 70 },
            rain = r.map { rain(it) },
            snowfall = r.map { 0.0 },
            visibility = r.map { 20000.0 },
            wind_gusts_10m = r.map { wind(it) * 1.3 },
            snow_depth = r.map { 0.0 }
        )
    }

    private fun types(l: List<com.weatherfocus.app.data.model.CustomAlertMatch>) = l.map { it.type }

    @Test fun rulesSurviveJsonRoundTrip() {
        val r = CustomAlertRules.defaults().copy(floodEnabled = true, floodMm = 123.0, rainProbValue = 55)
        val back = parseRules(rulesToJson(r))
        assertEquals(r, back)
    }

    @Test fun missingJsonFieldsKeepDefaults() {
        val r = parseRules("{\"enabled\":true}")!!
        assertTrue(r.enabled)
        assertEquals(24, r.horizonHours)
        assertEquals(3, r.leadTimeHours)
    }

    @Test fun defaultsTurnEverythingOn() {
        val d = CustomAlertRules.defaults()
        assertTrue(d.enabled && d.floodEnabled && d.rainProbEnabled && d.carIceEnabled && d.carSnowEnabled && d.blackIceEnabled)
        assertTrue(d.windSpeedEnabled && d.freezingRainEnabled && d.snowstormEnabled && d.officialEnabled)
    }

    @Test fun floodAlertWhenGroundIsSaturatedAndMoreRainComes() {
        val now = evening()
        val h = hourly(now, rain = { 1.0 }) // 48 mm over the next 48 h
        val out = CustomWeatherAlertEngine.evaluate(h, CustomAlertRules.defaults(), now, FloodContext(rainPast5dMm = 50.0))
        assertTrue(types(out).contains(AlertRuleType.FLOOD))
    }

    @Test fun noFloodAlertWhenItIsDry() {
        val now = evening()
        val out = CustomWeatherAlertEngine.evaluate(hourly(now), CustomAlertRules.defaults(), now, FloodContext(rainPast5dMm = 5.0))
        assertFalse(types(out).contains(AlertRuleType.FLOOD))
    }

    @Test fun rainChanceAlert() {
        val now = evening()
        val h = hourly(now, pop = { if (it == 4) 85 else 10 })
        val out = CustomWeatherAlertEngine.evaluate(h, CustomAlertRules.defaults(), now)
        val m = out.firstOrNull { it.type == AlertRuleType.RAIN_PROBABILITY }
        assertNotNull(m)
        assertTrue(m!!.leadWarning)
    }

    @Test fun strongWindIsReportedInMetresPerSecond() {
        val now = evening()
        val out = CustomWeatherAlertEngine.evaluate(hourly(now, wind = { if (it == 3) 60.0 else 5.0 }), CustomAlertRules.defaults(), now)
        val m = out.first { it.type == AlertRuleType.WIND_SPEED }
        assertTrue(m.detail.contains("m/s"))
        assertFalse(m.detail.contains("km/h"))
    }

    @Test fun freezingRainSuppressesDuplicateIceAlert() {
        val now = evening()
        val h = hourly(now, temp = { -1.0 }, rain = { if (it in 4..6) 1.0 else 0.0 }, code = { if (it in 4..6) 66 else 3 })
        val out = CustomWeatherAlertEngine.evaluate(h, CustomAlertRules.defaults(), now)
        val t = types(out)
        assertTrue(t.contains(AlertRuleType.FREEZING_RAIN))
        assertFalse(t.contains(AlertRuleType.BLACK_ICE))
        assertEquals(AlertSeverity.RED, out.first { it.type == AlertRuleType.FREEZING_RAIN }.severity)
    }

    @Test fun carIceAlertTheEveningBefore() {
        val now = evening()
        val h = hourly(now, temp = { -5.0 }, cloud = { 10 })
        val out = CustomWeatherAlertEngine.evaluate(h, CustomAlertRules.defaults(), now)
        val m = out.firstOrNull { it.type == AlertRuleType.CAR_ICE }
        assertNotNull(m)
        assertTrue(m!!.leadWarning)
    }

    @Test fun nothingFiresWhenDisabled() {
        val now = evening()
        val out = CustomWeatherAlertEngine.evaluate(hourly(now, temp = { -20.0 }), CustomAlertRules(enabled = false), now)
        assertTrue(out.isEmpty())
    }

    @Test fun officialWarningsAreParsed() {
        val json = """
            {"type":"FeatureCollection","features":[{"type":"Feature",
              "properties":{"event":"icing","eventAwarenessName":"Ising","awareness_level":"3; orange; Severe",
                            "description":"Ice on roads","instruction":"Drive carefully","area":"Buskerud","geographicDomain":"land"},
              "when":{"interval":["2020-01-01T00:00:00+00:00","2099-01-01T00:00:00+00:00"]}}]}
        """.trimIndent()
        val out = YrMerger.parseOfficialAlerts(JsonParser.parseString(json))
        assertEquals(1, out.size)
        assertEquals(AlertSeverity.ORANGE, out[0].severity)
        assertTrue(out[0].official)
        assertTrue(out[0].label.contains("Ising"))
    }

    @Test fun yrOverridesOpenMeteoAndShiftsPrecipitationByOneHour() {
        val t0 = 3_600_000L * 497_222L
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val fmt = keyFmt
        val times = listOf(t0, t0 + 3_600_000L, t0 + 7_200_000L).map { fmt.format(Date(it)) }
        val z = listOf(0.0, 0.0, 0.0)
        val om = OpenMeteoHourly(
            time = times, temperature_2m = listOf(9.0, 9.0, 9.0), precipitation_probability = listOf(0, 0, 0),
            weather_code = listOf(0, 0, 0), wind_speed_10m = z, uv_index = z, rain = z, snowfall = z
        )
        val yr = MetNoResponse(
            MetNoProperties(
                listOf(
                    MetNoTimeseries(
                        utc.format(Date(t0)),
                        MetNoData(
                            MetNoInstant(MetNoDetails(-3.2, 4.0, 80.0, 1010.0)),
                            MetNoNextHours(MetNoSummary("heavysnow"), MetNoPeriodDetails(2.0, 90.0, 0.0))
                        )
                    )
                )
            )
        )
        val merged = YrMerger.mergeHourly(om, yr)!!
        assertEquals(-3.2, merged.temperature_2m!![0]!!, 0.001)
        assertEquals(75, merged.weather_code!![0])
        assertEquals(90, merged.precipitation_probability!![0])
        assertEquals(14.4, merged.wind_speed_10m!![0]!!, 0.001) // 4 m/s -> km/h
        assertEquals(2.4, merged.snowfall!![1]!!, 0.001)       // shifted to the following hour
        assertEquals(9.0, merged.temperature_2m!![1]!!, 0.001) // hours yr.no does not cover keep Open-Meteo's values
    }
}
