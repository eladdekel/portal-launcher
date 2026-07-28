package com.iblu01.portallauncher.ui.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iblu01.portallauncher.domain.model.ForecastPoint
import com.iblu01.portallauncher.PillRepository
import java.util.Calendar
import kotlin.math.roundToInt

/** UI snapshot shown on the clock screen, sourced from the Home Assistant weather entity. */
data class WeatherUi(
    val temp: String = "--°",
    val indoorTemp: String = "22°",
    val city: String = "",
    val condition: String = "",
    val glyph: WeatherGlyph = WeatherGlyph(showLuminary = false),
    val hourly: List<ForecastPoint> = emptyList(),
    val daily: List<ForecastPoint> = emptyList(),
)

/**
 * Derives [WeatherUi] from the HA `weather.*` entity and its subscribed forecasts,
 * observed through [PillRepository]'s lightweight change notifier.
 */
class WeatherController(context: Context, private val pills: PillRepository) {
    var state by mutableStateOf(WeatherUi())
        private set

    private val listener = PillRepository.Listener { rebuild() }

    fun start() { pills.addListener(listener) }
    fun stop() { pills.removeListener(listener) }
    fun refreshNow() = rebuild()

    private fun rebuild() {
        val entity = pills.weatherEntityId?.let { pills.latestStates[it] } ?: return
        val condition = entity.state
        val temp = entity.attributes.optDouble("temperature").let { if (it.isNaN()) null else it }
        state = WeatherUi(
            temp = temp?.let { "${it.roundToInt()}°" } ?: "--°",
            condition = weatherLabel(condition),
            glyph = weatherGlyph(condition, isNight()),
            hourly = pills.hourlyForecast,
            daily = pills.dailyForecast,
        )
    }

    private fun isNight(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h < 7 || h >= 21
    }
}

/** HA condition string → animated glyph. */
fun weatherGlyph(condition: String, night: Boolean): WeatherGlyph = when (condition.lowercase()) {
    "sunny", "clear" -> WeatherGlyph(night = false, showCloud = false)
    "clear-night" -> WeatherGlyph(night = true, showCloud = false)
    "partlycloudy" -> WeatherGlyph(night = night)
    "cloudy" -> WeatherGlyph(showLuminary = false)
    "fog" -> WeatherGlyph(precip = Precip.FOG, showLuminary = false)
    "rainy" -> WeatherGlyph(night = night, precip = Precip.RAIN)
    "pouring" -> WeatherGlyph(precip = Precip.RAIN, showLuminary = false)
    "lightning", "lightning-rainy" -> WeatherGlyph(precip = Precip.THUNDER, showLuminary = false)
    "snowy", "snowy-rainy", "hail" -> WeatherGlyph(precip = Precip.SNOW, showLuminary = false)
    "windy", "windy-variant", "exceptional" -> WeatherGlyph(showLuminary = false)
    else -> WeatherGlyph(showLuminary = false)
}

/** HA condition string → French label. */
fun weatherLabel(condition: String): String = when (condition.lowercase()) {
    "sunny", "clear" -> "Ensoleillé"
    "clear-night" -> "Ciel dégagé"
    "partlycloudy" -> "Partiellement nuageux"
    "cloudy" -> "Couvert"
    "fog" -> "Brouillard"
    "rainy" -> "Pluie"
    "pouring" -> "Fortes pluies"
    "lightning" -> "Orage"
    "lightning-rainy" -> "Orage pluvieux"
    "snowy" -> "Neige"
    "snowy-rainy" -> "Neige et pluie"
    "hail" -> "Grêle"
    "windy", "windy-variant" -> "Venteux"
    "exceptional" -> "Exceptionnel"
    else -> condition.replaceFirstChar { it.uppercase() }
}
