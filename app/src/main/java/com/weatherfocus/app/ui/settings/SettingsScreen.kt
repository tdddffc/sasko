package com.weatherfocus.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherfocus.app.data.model.CustomAlertRules
import com.weatherfocus.app.data.model.DoNotDisturbSettings
import com.weatherfocus.app.data.model.ThemeMode
import com.weatherfocus.app.data.prefs.CountryCatalog
import com.weatherfocus.app.notification.NotificationHelper
import com.weatherfocus.app.ui.WeatherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: WeatherViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var countryMenuExpanded by remember { mutableStateOf(false) }
    var cityQuery by remember(state.settings.cityName) { mutableStateOf(state.settings.cityName) }
    // Only one group is open at a time, so the screen stays short.
    var openGroup by remember { mutableStateOf<String?>(null) }
    fun toggle(id: String) { openGroup = if (openGroup == id) null else id }
    val settings = state.settings
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
        ) {
            SettingsGroup(
                emoji = "\uD83D\uDCCD", title = "Location",
                subtitle = "${settings.cityName}, ${settings.countryLabel}",
                expanded = openGroup == "location", onToggle = { toggle("location") }
            ) {
                ExposedDropdownMenuBox(expanded = countryMenuExpanded, onExpandedChange = { countryMenuExpanded = it }) {
                    OutlinedTextField(
                        value = state.settings.countryLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Country") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = countryMenuExpanded,
                        onDismissRequest = { countryMenuExpanded = false }
                    ) {
                        CountryCatalog.ALL.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(entry.label) },
                                onClick = {
                                    viewModel.onCountrySelected(entry.code, entry.label)
                                    countryMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer4()

                Column {
                    OutlinedTextField(
                        value = cityQuery,
                        onValueChange = {
                            cityQuery = it
                            viewModel.onCityQueryChanged(it)
                        },
                        label = { Text("City") },
                        placeholder = { Text("Start typing a city name...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.citySuggestions.isNotEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Column {
                                state.citySuggestions.take(6).forEach { place ->
                                    DropdownMenuItem(
                                        text = { Text(place.displayLabel) },
                                        onClick = {
                                            cityQuery = place.name
                                            viewModel.onCitySelected(place)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }


            }

            CollapsibleAlertRulesSection(
                savedRules = settings.customAlertRules,
                onSave = { viewModel.updateAlertRules(it) }
            )

            SettingsGroup(
                emoji = "\uD83E\uDDE9", title = "Widgets",
                subtitle = widgetSummary(settings.widget),
                expanded = openGroup == "widgets", onToggle = { toggle("widgets") }
            ) {
                WidgetSettingsSection(
                    ws = settings.widget,
                    onChange = { viewModel.updateWidgetSettings(it) }
                )
            }

            SettingsGroup(
                emoji = "\uD83D\uDD14", title = "Notifications",
                subtitle = if (settings.doNotDisturb.enabled) "Quiet hours on" else "Quiet hours off",
                expanded = openGroup == "notifications", onToggle = { toggle("notifications") }
            ) {
                SectionTitle("Do Not Disturb")
                Text(
                    "Silence weather-alert notifications during a daily quiet-hours window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer4()
                DoNotDisturbEditor(dnd = state.settings.doNotDisturb, onChange = { viewModel.updateDoNotDisturb(it) })


                Spacer4()
                HorizontalDivider()
                Spacer4()
                SectionTitle("Test notification")
                Text(
                    "If custom alerts are enabled below but you never see a notification, first make sure this phone actually delivers notifications for this app (Android may block them silently even with everything configured correctly). Tap the button to send a test notification right now, bypassing Do Not Disturb.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer4()
                val context = LocalContext.current
                Button(
                    onClick = { NotificationHelper.showTestNotification(context) },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                    Text("Send test notification")
                }
                val allowed = NotificationHelper.areNotificationsAllowed(context)
                Text(
                    if (allowed) "Status: notifications are allowed for this app." else "Status: notifications are BLOCKED for this app - open notification settings and turn them on.",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (allowed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = { NotificationHelper.openSystemNotificationSettings(context) },
                    modifier = Modifier.padding(top = 4.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) { Text("Open notification settings") }
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) { Text("Battery: allow background use") }
                Text(
                    "If nothing arrives: set this app's battery usage to Unrestricted/\"Don't optimize\" (Samsung, Xiaomi, Huawei, OnePlus etc. otherwise stop background checks, so alerts and widget updates only arrive when you open the app).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )


            }

            SettingsGroup(
                emoji = "\uD83D\uDEE0\uFE0F", title = "Alert tools & history",
                subtitle = "Snooze \u00B7 weekend car alerts \u00B7 sent alerts",
                expanded = openGroup == "alerttools", onToggle = { toggle("alerttools") }
            ) { AlertToolsSection() }

            SettingsGroup(
                emoji = "\uD83E\uDE7A", title = "Health check",
                subtitle = "Why am I not getting alerts?",
                expanded = openGroup == "health", onToggle = { toggle("health") }
            ) { HealthSection() }

            SettingsGroup(
                emoji = "\uD83C\uDFE0", title = "Saved places",
                subtitle = if (state.savedPlaces.isEmpty()) "Home, work, cabin\u2026" else "${state.savedPlaces.size} saved",
                expanded = openGroup == "places", onToggle = { toggle("places") }
            ) {
                PlacesSection(
                    current = settings.cityName, places = state.savedPlaces,
                    onSaveCurrent = { viewModel.saveCurrentPlace() },
                    onSwitch = { viewModel.switchToPlace(it) },
                    onDelete = { viewModel.deletePlace(it) }
                )
            }

            SettingsGroup(
                emoji = "\uD83C\uDFA8", title = "Appearance & units",
                subtitle = "${themeLabel(settings.themeMode)} \u00B7 ${if (settings.useFahrenheit) "\u00B0F" else "\u00B0C"}",
                expanded = openGroup == "appearance", onToggle = { toggle("appearance") }
            ) {
                SectionTitle("Theme")
                ThemeModePicker(current = settings.themeMode, onSelect = { viewModel.onThemeModeSelected(it) })
                Spacer4()
                HorizontalDivider()
                Spacer4()
                SectionTitle("Units")
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Use Fahrenheit", modifier = Modifier.weight(1f))
                    Switch(checked = settings.useFahrenheit, onCheckedChange = { viewModel.onFahrenheitToggled(it) }, colors = visibleSwitchColors())
                }
            }
        }
    }
}



private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System theme"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun widgetSummary(ws: com.weatherfocus.app.data.model.WidgetSettings): String {
    val look = when (ws.style) {
        com.weatherfocus.app.data.model.WidgetStyle.WEATHER -> "Weather colours"
        com.weatherfocus.app.data.model.WidgetStyle.DARK -> "Dark"
        com.weatherfocus.app.data.model.WidgetStyle.LIGHT -> "Light"
        com.weatherfocus.app.data.model.WidgetStyle.AUTO -> "Follow phone (light/dark)"
    }
    return "$look \u00B7 ${ws.tiles.size} info tiles"
}

/** One collapsible settings card: emoji + title + one-line summary, chevron, content appears when open. */
@Composable
internal fun SettingsGroup(
    emoji: String,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(emoji, fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    if (!expanded) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) { content() }
            }
        }
    }
}

/**
 * "Custom Weather Alerts" as a collapsible card: starts collapsed so opening it doesn't push the user
 * all the way down the Settings screen. While expanded, edits are kept in local draft state only (not
 * written on every slider tick) - tapping Save commits them all at once and collapses the card back down.
 */
@Composable
private fun CollapsibleAlertRulesSection(savedRules: CustomAlertRules, onSave: (CustomAlertRules) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var draft by remember(expanded) { mutableStateOf(savedRules) }
    var applied by remember(expanded) { mutableStateOf(false) }
    val dirty = expanded && draft != savedRules

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Weather & Winter Alerts", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    if (!expanded) {
                        Text(
                            if (savedRules.enabled) "Enabled \u00B7 tap to edit" else "Disabled \u00B7 tap to edit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer4()
                    AlertRulesEditor(rules = draft, onChange = { draft = it })

                    Spacer4()
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        // Explicit colors here on purpose: the default filled/outlined button colors come from
                        // colorScheme.primary, which this app's dark theme sets to a near-black graphite (by
                        // design, for the rest of the UI) - that made "Save" blend invisibly into the dark
                        // background and made "Cancel"'s text (which defaults to the primary color too) almost
                        // unreadable. secondary/onSecondary and onSurface stay high-contrast in both themes.
                        Button(
                            onClick = {
                                onSave(draft)
                                expanded = false
                            },
                            enabled = dirty,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                            Text("Save")
                        }
                        androidx.compose.material3.OutlinedButton(
                            onClick = { expanded = false },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text("Cancel")
                        }
                    }
                    Spacer4()
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            val d = CustomAlertRules.defaults()
                            draft = d
                            onSave(d)
                            applied = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) { Text("Apply default settings (everything on)") }
                    Text(
                        if (applied) "\u2705 Default settings applied and saved \u2013 all alerts are on."
                        else "Turns on every alert above with the default numbers and saves right away.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModePicker(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        ThemeChip("System", current == ThemeMode.SYSTEM) { onSelect(ThemeMode.SYSTEM) }
        ThemeChip("Light", current == ThemeMode.LIGHT) { onSelect(ThemeMode.LIGHT) }
        ThemeChip("Dark", current == ThemeMode.DARK) { onSelect(ThemeMode.DARK) }
    }
}

@Composable
internal fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null
    )
}

@Composable
private fun DoNotDisturbEditor(dnd: DoNotDisturbSettings, onChange: (DoNotDisturbSettings) -> Unit) {
    RowSwitch("Enable Do Not Disturb", dnd.enabled) { onChange(dnd.copy(enabled = it)) }
    if (!dnd.enabled) return

    Spacer4()
    ExpandableParamSlider(
        label = { "From ${formatHour(it)}" },
        value = dnd.startHour,
        range = 0..23
    ) { onChange(dnd.copy(startHour = it)) }

    ExpandableParamSlider(
        label = { "To ${formatHour(it)}" },
        value = dnd.endHour,
        range = 0..23
    ) { onChange(dnd.copy(endHour = it)) }
}

private fun formatHour(hour: Int): String = String.format(java.util.Locale.US, "%02d:00", hour)

/**
 * One parameter, one collapsible row: tap the row to reveal its slider, drag it, and it auto-collapses
 * back to a compact summary the moment you let go - so a settings section with several sliders doesn't
 * turn into a wall of always-open controls, and each parameter manages its own expand/collapse state
 * independently of the others.
 *
 * The label is driven off [dragValue] while expanded (updated on every [Slider.onValueChange] frame, not
 * just when the drag finishes), so the number next to the row updates live as you move the thumb instead
 * of only jumping once you stop - this is what was previously missing since the label read the *committed*
 * value only.
 */
@Composable
private fun ExpandableParamSlider(
    label: (Int) -> String,
    value: Int,
    range: IntRange,
    onCommit: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var dragValue by remember(value, expanded) { mutableStateOf(value.toFloat()) }
    val displayValue = if (expanded) dragValue.toInt() else value

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(label(displayValue), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
            // Explicit colors: the default Slider colors come from colorScheme.primary, which this app's
            // dark theme sets to a near-black graphite - against the dark background that made the track
            // and thumb nearly invisible. secondary (a warm amber/yellow in both themes) stays visible.
            Slider(
                value = dragValue,
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    onCommit(dragValue.toInt())
                    expanded = false
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first - 1).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                    activeTickColor = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.5f),
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    inactiveTickColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }
    }
}

private fun msOf(kmh: Double): Int = Math.round(kmh / 3.6).toInt()
private fun kmhOf(ms: Int): Double = Math.round(ms * 3.6 * 10) / 10.0

@Composable
private fun AlertRulesEditor(rules: CustomAlertRules, onChange: (CustomAlertRules) -> Unit) {
    RowSwitch("Enable weather alerts", rules.enabled) { onChange(rules.copy(enabled = it)) }
    if (!rules.enabled) return

    Spacer4()
    ExpandableParamSlider(
        label = { "Check the next ${it}h" },
        value = rules.horizonHours.coerceIn(1, 86),
        range = 1..86
    ) { onChange(rules.copy(horizonHours = it)) }

    ExpandableParamSlider(
        label = { "Warn up to ${it}h before" },
        value = rules.leadTimeHours.coerceIn(1, 48),
        range = 1..48
    ) { onChange(rules.copy(leadTimeHours = it)) }

    // ---------------- General ----------------
    Spacer4()
    Text("\uD83C\uDF26\uFE0F General", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
    Spacer4()

    RowSwitch("Temperature above ${rules.tempAboveValue.toInt()}\u00B0C", rules.tempAboveEnabled) { onChange(rules.copy(tempAboveEnabled = it)) }
    if (rules.tempAboveEnabled) {
        ExpandableParamSlider(label = { "$it\u00B0C" }, value = rules.tempAboveValue.toInt(), range = -10..45) {
            onChange(rules.copy(tempAboveValue = it.toDouble()))
        }
    }

    RowSwitch("Temperature below ${rules.tempBelowValue.toInt()}\u00B0C", rules.tempBelowEnabled) { onChange(rules.copy(tempBelowEnabled = it)) }
    if (rules.tempBelowEnabled) {
        ExpandableParamSlider(label = { "$it\u00B0C" }, value = rules.tempBelowValue.toInt(), range = -30..20) {
            onChange(rules.copy(tempBelowValue = it.toDouble()))
        }
    }

    RowSwitch("UV index above ${rules.uvIndexValue.toInt()}", rules.uvIndexEnabled) { onChange(rules.copy(uvIndexEnabled = it)) }
    if (rules.uvIndexEnabled) {
        ExpandableParamSlider(label = { "UV $it" }, value = rules.uvIndexValue.toInt(), range = 1..12) {
            onChange(rules.copy(uvIndexValue = it.toDouble()))
        }
    }

    RowSwitch("\uD83D\uDCA8 Strong wind (\u2265 ${msOf(rules.windSpeedValue)} m/s or gusts \u2265 ${msOf(rules.windGustValue)} m/s)", rules.windSpeedEnabled) { onChange(rules.copy(windSpeedEnabled = it)) }
    if (rules.windSpeedEnabled) {
        ExpandableParamSlider(label = { "Steady wind \u2265 $it m/s" }, value = msOf(rules.windSpeedValue).coerceIn(2, 40), range = 2..40) {
            onChange(rules.copy(windSpeedValue = kmhOf(it)))
        }
        ExpandableParamSlider(label = { "Gusts \u2265 $it m/s" }, value = msOf(rules.windGustValue).coerceIn(3, 55), range = 3..55) {
            onChange(rules.copy(windGustValue = kmhOf(it)))
        }
    }

    RowSwitch("\uD83C\uDF27\uFE0F Rain chance above ${rules.rainProbValue}%", rules.rainProbEnabled) { onChange(rules.copy(rainProbEnabled = it)) }
    if (rules.rainProbEnabled) {
        ExpandableParamSlider(label = { "Rain chance \u2265 $it%" }, value = rules.rainProbValue.coerceIn(10, 100), range = 10..100) {
            onChange(rules.copy(rainProbValue = it))
        }
    }

    RowSwitch("\u26C8\uFE0F Thunderstorm expected", rules.thunderstormEnabled) { onChange(rules.copy(thunderstormEnabled = it)) }

    RowSwitch("\uD83C\uDF0A Flood risk (rain 5 days + next 2 days \u2265 ${rules.floodMm.toInt()} mm)", rules.floodEnabled) { onChange(rules.copy(floodEnabled = it)) }
    if (rules.floodEnabled) {
        ExpandableParamSlider(label = { "Rain in last 5 days + next 48 h \u2265 $it mm" }, value = rules.floodMm.toInt().coerceIn(20, 250), range = 20..250) {
            onChange(rules.copy(floodMm = it.toDouble()))
        }
        Text(
            "Also looks at the nearby river's forecast flow (GloFAS) and warns if it is about to rise sharply.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // ---------------- Winter & Nordic ----------------
    Spacer4()
    Text("\u2744\uFE0F Winter & Nordic", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
    Text(
        "Tap a value to set the number that triggers the notification.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer4()

    RowSwitch("\u26A0\uFE0F Ice on the road (wet roads, air at or below ${rules.blackIceMaxTemp.toInt()}\u00B0C)", rules.blackIceEnabled) { onChange(rules.copy(blackIceEnabled = it)) }
    if (rules.blackIceEnabled) {
        ExpandableParamSlider(label = { "Wet roads and air at or below $it\u00B0C" }, value = rules.blackIceMaxTemp.toInt(), range = -10..5) {
            onChange(rules.copy(blackIceMaxTemp = it.toDouble()))
        }
    }

    RowSwitch("\uD83E\uDDCA Freezing rain (rain at or below ${rules.freezingRainMaxTemp.toInt()}\u00B0C)", rules.freezingRainEnabled) { onChange(rules.copy(freezingRainEnabled = it)) }
    if (rules.freezingRainEnabled) {
        ExpandableParamSlider(label = { "Rain at or below $it\u00B0C" }, value = rules.freezingRainMaxTemp.toInt(), range = -5..3) {
            onChange(rules.copy(freezingRainMaxTemp = it.toDouble()))
        }
    }

    RowSwitch("\uD83C\uDF28\uFE0F Snowfall (\u2265 ${rules.heavySnowValue.toInt()} cm/h)", rules.heavySnowEnabled) { onChange(rules.copy(heavySnowEnabled = it)) }
    if (rules.heavySnowEnabled) {
        ExpandableParamSlider(label = { "$it cm of snow per hour" }, value = rules.heavySnowValue.toInt(), range = 1..10) {
            onChange(rules.copy(heavySnowValue = it.toDouble()))
        }
    }

    RowSwitch("\uD83C\uDF2C\uFE0F Snowstorm (snow + wind \u2265 ${msOf(rules.snowstormWindValue)} m/s)", rules.snowstormEnabled) { onChange(rules.copy(snowstormEnabled = it)) }
    if (rules.snowstormEnabled) {
        ExpandableParamSlider(label = { "Snow with wind \u2265 $it m/s" }, value = msOf(rules.snowstormWindValue).coerceIn(3, 30), range = 3..30) {
            onChange(rules.copy(snowstormWindValue = kmhOf(it)))
        }
    }

    RowSwitch("\uD83E\uDD76 Wind chill below ${rules.windChillValue.toInt()}\u00B0C", rules.windChillEnabled) { onChange(rules.copy(windChillEnabled = it)) }
    if (rules.windChillEnabled) {
        ExpandableParamSlider(label = { "Feels like \u2264 $it\u00B0C" }, value = rules.windChillValue.toInt(), range = -40..5) {
            onChange(rules.copy(windChillValue = it.toDouble()))
        }
    }

    RowSwitch("\uD83C\uDF2B\uFE0F Low visibility below ${rules.lowVisibilityValue.toInt()} m", rules.lowVisibilityEnabled) { onChange(rules.copy(lowVisibilityEnabled = it)) }
    if (rules.lowVisibilityEnabled) {
        // Slider works in 100 m steps so it doesn't need thousands of tick marks.
        ExpandableParamSlider(label = { "Visibility \u2264 ${it * 100} m" }, value = (rules.lowVisibilityValue / 100).toInt().coerceIn(1, 50), range = 1..50) {
            onChange(rules.copy(lowVisibilityValue = it * 100.0))
        }
    }

    RowSwitch("\uD83D\uDCC9 Rapid temperature drop (\u2265 ${rules.rapidDropValue.toInt()}\u00B0C in 6h)", rules.rapidDropEnabled) { onChange(rules.copy(rapidDropEnabled = it)) }
    if (rules.rapidDropEnabled) {
        ExpandableParamSlider(label = { "Drop of $it\u00B0C within 6 hours" }, value = rules.rapidDropValue.toInt(), range = 2..25) {
            onChange(rules.copy(rapidDropValue = it.toDouble()))
        }
    }

    // ---------------- Car ----------------
    Spacer4()
    Text("\uD83D\uDE97 Car", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
    Text(
        "You get these the evening before (from 18:00) so you know whether to set the alarm earlier.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer4()

    RowSwitch("\uD83E\uDDCA Ice on the car in the morning (chance \u2265 ${rules.carIceChance}%)", rules.carIceEnabled) { onChange(rules.copy(carIceEnabled = it)) }
    if (rules.carIceEnabled) {
        ExpandableParamSlider(label = { "Warn when frost/ice chance is \u2265 $it%" }, value = rules.carIceChance, range = 10..95) {
            onChange(rules.copy(carIceChance = it))
        }
    }

    RowSwitch("\u2744\uFE0F Snow on the car overnight (\u2265 ${fmtCm(rules.carSnowValue)} cm)", rules.carSnowEnabled) { onChange(rules.copy(carSnowEnabled = it)) }
    if (rules.carSnowEnabled) {
        // Slider in 0.5 cm steps.
        ExpandableParamSlider(label = { "Snow overnight \u2265 ${fmtCm(it / 2.0)} cm" }, value = (rules.carSnowValue * 2).toInt(), range = 1..20) {
            onChange(rules.copy(carSnowValue = it / 2.0))
        }
    }
}

private fun fmtCm(v: Double): String = if (v == v.toInt().toDouble()) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)

@Composable
internal fun RowSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = visibleSwitchColors())
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
internal fun Spacer4() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
}


/**
 * Switch colours that stay clearly readable in dark mode: ON = bright green track with a white thumb,
 * OFF = dim grey track with a visible outline. (The theme's primary colour is near-black in dark mode,
 * so the default switch colours made ON and OFF look almost identical.)
 */
@Composable
internal fun visibleSwitchColors() = androidx.compose.material3.SwitchDefaults.colors(
    checkedThumbColor = androidx.compose.ui.graphics.Color.White,
    checkedTrackColor = androidx.compose.ui.graphics.Color(0xFF2EBD59),
    checkedBorderColor = androidx.compose.ui.graphics.Color(0xFF2EBD59),
    uncheckedThumbColor = androidx.compose.ui.graphics.Color(0xFFB0B3B8),
    uncheckedTrackColor = androidx.compose.ui.graphics.Color(0xFF3A3C40),
    uncheckedBorderColor = androidx.compose.ui.graphics.Color(0xFF8A8D93)
)


/** Widget settings, split into small labelled blocks; the long info-tile picker is its own collapsible list. */
@Composable
private fun WidgetSettingsSection(
    ws: com.weatherfocus.app.data.model.WidgetSettings,
    onChange: (com.weatherfocus.app.data.model.WidgetSettings) -> Unit
) {
    val hint = MaterialTheme.typography.bodySmall
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    var tilesOpen by remember { mutableStateOf(false) }
    val style = com.weatherfocus.app.data.model.WidgetStyle.values()

    Text(
        "Add a widget: long-press the home screen, choose Widgets, then Weather or Weather alerts. Stretch the Weather widget taller for more info; full-screen size shows everything.",
        style = hint, color = hintColor
    )

    Spacer4()
    Text("Look", fontWeight = FontWeight.SemiBold)
    ChipRow {
        ThemeChip("Weather colours", ws.style == style[0]) { onChange(ws.copy(style = style[0])) }
        ThemeChip("Dark", ws.style == style[1]) { onChange(ws.copy(style = style[1])) }
        ThemeChip("Light", ws.style == style[2]) { onChange(ws.copy(style = style[2])) }
    }
    if (ws.style == style[0]) {
        Text("Colour follows the weather in the next:", style = hint, color = hintColor, modifier = Modifier.padding(top = 6.dp))
        ChipRow {
            listOf(0 to "Now", 3 to "3 h", 6 to "6 h", 12 to "12 h", 24 to "24 h").forEach { (h, label) ->
                ThemeChip(label, ws.lookaheadHours == h) { onChange(ws.copy(lookaheadHours = h)) }
            }
        }
    }

    Spacer4()
    HorizontalDivider()
    Spacer4()
    Text("Show", fontWeight = FontWeight.SemiBold)
    RowSwitch("Alerts", ws.showAlert) { onChange(ws.copy(showAlert = it)) }
    RowSwitch("Hourly strip", ws.showHours) { onChange(ws.copy(showHours = it)) }
    RowSwitch("Next days", ws.showDays) { onChange(ws.copy(showDays = it)) }
    RowSwitch("Info tiles", ws.showTiles) { onChange(ws.copy(showTiles = it)) }
    RowSwitch("Sunrise & sunset", ws.showSun) { onChange(ws.copy(showSun = it)) }
    RowSwitch("What to wear icons (wide widget)", ws.showWear) { onChange(ws.copy(showWear = it)) }
    RowSwitch("Cover-car tip (frost on windscreen tomorrow)", ws.showCarTip) { onChange(ws.copy(showCarTip = it)) }

    Spacer4()
    HorizontalDivider()
    Spacer4()
    Text("Amounts", fontWeight = FontWeight.SemiBold)
    Text("Hours in the strip", style = hint, color = hintColor)
    ChipRow { listOf(4, 6, 8).forEach { n -> ThemeChip("$n h", ws.hoursCount == n) { onChange(ws.copy(hoursCount = n)) } } }
    Text("Days in the row", style = hint, color = hintColor, modifier = Modifier.padding(top = 6.dp))
    ChipRow { listOf(3, 5, 7).forEach { n -> ThemeChip("$n days", ws.daysCount == n) { onChange(ws.copy(daysCount = n)) } } }

    var snowSlider by remember(ws.snowHours) { mutableStateOf(ws.snowHours.toFloat()) }
    Text(
        "Snow tile adds up snowfall for the next ${snowSlider.toInt()} h",
        style = hint, color = hintColor, modifier = Modifier.padding(top = 6.dp)
    )
    Slider(
        value = snowSlider,
        onValueChange = { snowSlider = it },
        onValueChangeFinished = { onChange(ws.copy(snowHours = snowSlider.toInt().coerceIn(1, 12))) },
        valueRange = 1f..12f,
        steps = 10
    )

    Spacer4()
    HorizontalDivider()
    Spacer4()
    Row(
        modifier = Modifier.fillMaxWidth().clickable { tilesOpen = !tilesOpen }.padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Info tiles", fontWeight = FontWeight.SemiBold)
            Text("${ws.tiles.size} of 16 chosen \u00B7 tap to edit", style = hint, color = hintColor)
        }
        Icon(if (tilesOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
    }
    AnimatedVisibility(visible = tilesOpen) {
        TileOrganiser(ws, onChange)
    }
}

private val TileGreen = Color(0xFF2EBD59)

/** Tile picker: the chosen tiles as an ordered list grouped by widget row (reorderable), then the catalogue by topic. */
@Composable
private fun TileOrganiser(
    ws: com.weatherfocus.app.data.model.WidgetSettings,
    onChange: (com.weatherfocus.app.data.model.WidgetSettings) -> Unit
) {
    val hint = MaterialTheme.typography.bodySmall
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tiles = ws.tiles
    fun move(from: Int, to: Int) {
        if (to !in tiles.indices) return
        val list = tiles.toMutableList()
        val t = list.removeAt(from)
        list.add(to, t)
        onChange(ws.copy(tiles = list))
    }

    Column {
        Text(
            "The widget shows 4 tiles per row (more rows when you stretch it taller). " +
                "\u2728 tiles hide themselves when there is nothing to report.",
            style = hint, color = hintColor
        )
        RowSwitch("Hide \u2728 tiles when empty", ws.hideEmptyTiles) { onChange(ws.copy(hideEmptyTiles = it)) }

        Spacer4()
        Text("Your tiles", fontWeight = FontWeight.SemiBold)
        Card(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (tiles.isEmpty()) {
                    Text("No tiles chosen. Add some below.", style = hint, color = hintColor, modifier = Modifier.padding(vertical = 8.dp))
                }
                tiles.chunked(4).forEachIndexed { rowIdx, rowTiles ->
                    if (rowIdx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Widget row ${rowIdx + 1}", style = hint, color = hintColor, modifier = Modifier.padding(top = 2.dp, bottom = 2.dp))
                    rowTiles.forEachIndexed { j, tile ->
                        val i = rowIdx * 4 + j
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = hintColor, modifier = Modifier.width(22.dp))
                            Text(
                                "${tile.emoji}  ${tile.label}" + if (tile.hidesWhenEmpty) " \u2728" else "",
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
                            )
                            TileAction("\u25B2", enabled = i > 0) { move(i, i - 1) }
                            TileAction("\u25BC", enabled = i < tiles.size - 1) { move(i, i + 1) }
                            TileAction("\u2715", enabled = true) { onChange(ws.copy(tiles = tiles - tile)) }
                        }
                    }
                }
            }
        }

        Spacer4()
        Text("Add tiles", fontWeight = FontWeight.SemiBold)
        com.weatherfocus.app.data.model.TileGroup.values().forEach { group ->
            Text(
                group.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = hintColor,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
            com.weatherfocus.app.data.model.WidgetTile.values().filter { it.group == group }.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                ) {
                    pair.forEach { tile ->
                        val selected = tile in tiles
                        val full = !selected && tiles.size >= 16
                        TilePickCell(
                            text = "${tile.emoji} ${tile.label}" + if (tile.hidesWhenEmpty) " \u2728" else "",
                            selected = selected,
                            dimmed = full,
                            modifier = Modifier.weight(1f)
                        ) {
                            val next = if (selected) tiles - tile else if (tiles.size < 16) tiles + tile else tiles
                            onChange(ws.copy(tiles = next))
                        }
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer4()
        ChipRow {
            ThemeChip("Reset to recommended", false) {
                onChange(ws.copy(tiles = com.weatherfocus.app.data.model.WidgetSettings().tiles))
            }
            ThemeChip("Clear all", false) { onChange(ws.copy(tiles = emptyList())) }
        }
    }
}

@Composable
private fun TileAction(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        symbol,
        modifier = Modifier
            .let { if (enabled) it.clickable { onClick() } else it }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    )
}

@Composable
private fun TilePickCell(text: String, selected: Boolean, dimmed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (selected) TileGreen.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val borderColor = if (selected) TileGreen else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, shape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dimmed) 0.4f else 1f)
        )
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = TileGreen, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        content = content
    )
}
