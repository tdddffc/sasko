package com.weatherfocus.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weatherfocus.app.data.alert.CustomWeatherAlertEngine
import com.weatherfocus.app.data.model.AppSettings
import com.weatherfocus.app.data.model.CustomAlertMatch
import com.weatherfocus.app.data.model.CustomAlertRules
import com.weatherfocus.app.data.model.DoNotDisturbSettings
import com.weatherfocus.app.data.model.GeoPlace
import com.weatherfocus.app.data.model.ThemeMode
import com.weatherfocus.app.data.model.WidgetSettings
import com.weatherfocus.app.widget.WidgetRenderer
import com.weatherfocus.app.data.prefs.SettingsRepository
import com.weatherfocus.app.data.repository.WeatherBundle
import com.weatherfocus.app.data.repository.WeatherRepository
import com.weatherfocus.app.notification.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class WeatherUiState(
    val settings: AppSettings = AppSettings(),
    val bundle: WeatherBundle? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val citySuggestions: List<GeoPlace> = emptyList(),
    val activeAlerts: List<CustomAlertMatch> = emptyList(),
    val lastUpdatedMillis: Long? = null,
    val savedPlaces: List<com.weatherfocus.app.data.prefs.SavedPlace> = emptyList()
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val weatherRepository = WeatherRepository()

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private var citySearchJob: Job? = null

    /** Keys (type + trigger time) of alerts already pushed as a phone notification, so a manual refresh doesn't re-spam them. */

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            // Shared store: show the last known weather instantly, then refresh in the background.
            val cached = com.weatherfocus.app.widget.WidgetCache.load(getApplication())
            val cachedBundle = cached?.bundle
            _uiState.value = _uiState.value.copy(
                settings = settings,
                savedPlaces = com.weatherfocus.app.data.prefs.PlacesStore.load(getApplication()),
                bundle = cachedBundle,
                lastUpdatedMillis = cached?.fetchedAt,
                activeAlerts = if (cachedBundle != null)
                    CustomWeatherAlertEngine.evaluateAll(cachedBundle.hourlyForAlerts, settings.customAlertRules, cachedBundle.floodContext, cachedBundle.officialAlerts)
                else emptyList()
            )
            refresh()
        }
    }

    /* ---------- saved places (each keeps its own alert rules) ---------- */

    private fun currentAsPlace(): com.weatherfocus.app.data.prefs.SavedPlace {
        val s = _uiState.value.settings
        return com.weatherfocus.app.data.prefs.SavedPlace(
            s.cityName, s.countryCode, s.countryLabel, s.latitude, s.longitude,
            com.weatherfocus.app.data.model.rulesToJson(s.customAlertRules)
        )
    }

    fun saveCurrentPlace() {
        val list = com.weatherfocus.app.data.prefs.PlacesStore.upsert(getApplication(), currentAsPlace())
        _uiState.value = _uiState.value.copy(savedPlaces = list)
    }

    fun switchToPlace(p: com.weatherfocus.app.data.prefs.SavedPlace) {
        viewModelScope.launch {
            // Keep the rules of the place we are leaving (if it is a saved one), then load the new place's rules.
            val cur = currentAsPlace()
            val known = com.weatherfocus.app.data.prefs.PlacesStore.load(getApplication())
            if (known.any { it.name.equals(cur.name, true) && it.countryCode == cur.countryCode }) {
                com.weatherfocus.app.data.prefs.PlacesStore.upsert(getApplication(), cur)
            }
            settingsRepository.setLocation(p.name, p.countryCode, p.countryLabel, p.latitude, p.longitude)
            val rules = com.weatherfocus.app.data.model.parseRules(p.rulesJson) ?: _uiState.value.settings.customAlertRules
            settingsRepository.updateAlertRules(rules)
            _uiState.value = _uiState.value.copy(
                settings = _uiState.value.settings.copy(
                    cityName = p.name, countryCode = p.countryCode, countryLabel = p.countryLabel,
                    latitude = p.latitude, longitude = p.longitude, customAlertRules = rules
                ),
                savedPlaces = com.weatherfocus.app.data.prefs.PlacesStore.load(getApplication())
            )
            refresh()
        }
    }

    fun deletePlace(p: com.weatherfocus.app.data.prefs.SavedPlace) {
        _uiState.value = _uiState.value.copy(savedPlaces = com.weatherfocus.app.data.prefs.PlacesStore.remove(getApplication(), p))
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val settings = settingsRepository.settingsFlow.first()
            try {
                val bundle = weatherRepository.loadWeather(settings)
                val alerts = CustomWeatherAlertEngine.evaluateAll(bundle.hourlyForAlerts, settings.customAlertRules, bundle.floodContext, bundle.officialAlerts)
                _uiState.value = _uiState.value.copy(
                    settings = settings,
                    bundle = bundle,
                    isLoading = false,
                    activeAlerts = alerts,
                    lastUpdatedMillis = System.currentTimeMillis()
                )
                notifyNewAlerts(alerts, settings.doNotDisturb)
                // Keep any home-screen widgets in step with what the app just loaded.
                if (bundle.current.available) {
                    val now = System.currentTimeMillis()
                    com.weatherfocus.app.widget.WidgetCache.save(getApplication(), bundle, now)
                    runCatching { WidgetRenderer.renderAll(getApplication(), settings, bundle, fetchedAt = now) }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Couldn't load weather. Check your connection.")
            }
        }
    }

    /** Pushes a phone notification (subject to Do Not Disturb) for any imminent alert we haven't already notified about. */
    private fun notifyNewAlerts(alerts: List<CustomAlertMatch>, dnd: DoNotDisturbSettings) {
        NotificationHelper.notifyNew(getApplication(), alerts, dnd)
    }

    fun onCityQueryChanged(query: String) {
        citySearchJob?.cancel()
        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(citySuggestions = emptyList())
            return
        }
        citySearchJob = viewModelScope.launch {
            delay(300) // debounce while the user is still typing
            val results = weatherRepository.searchCities(query)
            val countryFilter = _uiState.value.settings.countryCode
            val filtered = results.filter { it.countryCode.equals(countryFilter, ignoreCase = true) }
            _uiState.value = _uiState.value.copy(citySuggestions = filtered.ifEmpty { results })
        }
    }

    fun onCountrySelected(code: String, label: String) {
        viewModelScope.launch {
            val current = _uiState.value.settings
            settingsRepository.setLocation(current.cityName, code, label, current.latitude, current.longitude)
            _uiState.value = _uiState.value.copy(
                settings = current.copy(countryCode = code, countryLabel = label),
                citySuggestions = emptyList()
            )
        }
    }

    fun onCitySelected(place: GeoPlace) {
        viewModelScope.launch {
            settingsRepository.setLocation(
                place.name, place.countryCode ?: _uiState.value.settings.countryCode,
                place.country ?: _uiState.value.settings.countryLabel, place.latitude, place.longitude
            )
            _uiState.value = _uiState.value.copy(citySuggestions = emptyList())
            refresh()
        }
    }

    fun onFahrenheitToggled(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFahrenheit(value)
            _uiState.value = _uiState.value.copy(settings = _uiState.value.settings.copy(useFahrenheit = value))
        }
    }

    fun onOpenWeatherKeyChanged(key: String) {
        viewModelScope.launch {
            settingsRepository.setOpenWeatherApiKey(key)
            _uiState.value = _uiState.value.copy(settings = _uiState.value.settings.copy(openWeatherApiKey = key))
        }
    }

    fun updateAlertRules(rules: CustomAlertRules) {
        viewModelScope.launch {
            settingsRepository.updateAlertRules(rules)
            _uiState.value = _uiState.value.copy(settings = _uiState.value.settings.copy(customAlertRules = rules))
            refresh()
        }
    }

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
            _uiState.value = _uiState.value.copy(settings = _uiState.value.settings.copy(themeMode = mode))
        }
    }

    fun updateDoNotDisturb(dnd: DoNotDisturbSettings) {
        viewModelScope.launch {
            settingsRepository.setDoNotDisturb(dnd)
            _uiState.value = _uiState.value.copy(settings = _uiState.value.settings.copy(doNotDisturb = dnd))
        }
    }

    fun updateWidgetSettings(ws: WidgetSettings) {
        viewModelScope.launch {
            settingsRepository.updateWidgetSettings(ws)
            val updated = _uiState.value.settings.copy(widget = ws)
            _uiState.value = _uiState.value.copy(settings = updated)
            redrawWidgets(updated)
        }
    }

    /** Redraws the widgets straight away from the weather already on screen, so setting changes show instantly. */
    private fun redrawWidgets(settings: AppSettings) {
        val bundle = _uiState.value.bundle ?: return
        runCatching { WidgetRenderer.renderAll(getApplication(), settings, bundle) }
    }
}
