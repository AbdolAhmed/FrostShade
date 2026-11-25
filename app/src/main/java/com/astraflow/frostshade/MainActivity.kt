package com.astraflow.frostshade

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.astraflow.tool.config.ConfigKeys
import com.astraflow.tool.config.LiquidControlCenterDefaults as Controls
import com.astraflow.tool.config.LiquidNotificationDefaults as Notifications
import com.astraflow.tool.config.NotificationBadgeSpec
import com.itos.coui.checkbox.Checkbox
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.dialog.InputDialog
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Footer
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.slider.Slider
import com.itos.coui.switch.Switch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CouiTheme { SettingsPage((application as FrostApplication).settings) }
        }
    }
}

@Composable
private fun SettingsPage(store: FrostSettingsStore) {
    val values by store.values.collectAsState()
    val context = LocalContext.current
    var reset by rememberSaveable { mutableStateOf(false) }

    fun on(item: Notifications.BoolItem) = values[item.key] as? Boolean ?: item.default
    fun number(item: Notifications.IntItem) = item.clamp(values[item.key] as? Int ?: item.default)
    fun text(item: Notifications.TextItem) = values[item.key] as? String ?: item.default

    LazyColumn(
        Modifier.fillMaxSize().background(CouiTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        item("title") {
            Column(Modifier.padding(horizontal = 32.dp).padding(top = 18.dp, bottom = 5.dp)) {
                Text(text = "FrostShade", kind = CouiTextKind.HomeTitle, textAlign = TextAlign.Start)
                Text(
                    text = "For ColorOS 15 and 16",
                    kind = CouiTextKind.ItemSummary,
                    color = CouiTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item("coolapk") {
            Group {
                ValueRow("Coolapk", "MuYuanXing") {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coolapk.com/u/28719807")))
                    }
                }
            }
        }
        item("switches") {
            Group(title = "Enabled for") {
                val notifications = values[ConfigKeys.LIQUID_NOTIFICATION_ENABLED] as? Boolean ?: true
                ToggleRow("Frosted stacked notifications", notifications) { store.set(ConfigKeys.LIQUID_NOTIFICATION_ENABLED, it) }
                Divider()
                val controls = values[ConfigKeys.LIQUID_CONTROL_CENTER_ENABLED] as? Boolean ?: true
                ToggleRow("Frosted control center", controls) { store.set(ConfigKeys.LIQUID_CONTROL_CENTER_ENABLED, it) }
            }
        }
        item("video") {
            OriginalPreview(Modifier.padding(horizontal = CouiPageDefaults.horizontalMargin).padding(top = 12.dp))
        }
        item("background") {
            Group(title = "Background") {
                SettingSlider("Background blur", Notifications.BACKGROUND_BLUR, values, store, " dp")
                SettingSlider("Background dim (light mode)", Notifications.BACKGROUND_DIM_LIGHT, values, store)
                SettingSlider("Background dim (dark mode)", Notifications.BACKGROUND_DIM_DARK, values, store)
                SettingSlider("Darken when bright behind", Notifications.BACKGROUND_BRIGHT_EXTRA, values, store)
                SettingSlider("Top darken", Notifications.BACKGROUND_TOP_DIM, values, store)
            }
        }
        item("glass") {
            Group(title = "Glass and light") {
                SettingSlider("Surface white density (light mode)", Notifications.SURFACE_LIGHT, values, store)
                SettingSlider("Surface white density (dark mode)", Notifications.SURFACE_DARK, values, store)
                SettingSlider("Shadow", Notifications.SHADOW, values, store)
                SettingSlider("Edge highlight strength", Notifications.HIGHLIGHT, values, store)
                ToggleRow("Edge highlight follows phone tilt", on(Notifications.HIGHLIGHT_TILT)) { store.set(Notifications.HIGHLIGHT_TILT.key, it) }
                Divider()
                ToggleRow("Glow on press", on(Notifications.PRESS_GLOW)) { store.set(Notifications.PRESS_GLOW.key, it) }
                Divider()
                ToggleRow("Shimmer", on(Notifications.FLOW_LIGHT)) { store.set(Notifications.FLOW_LIGHT.key, it) }
                Divider()
                ToggleRow("Ambient light", on(Notifications.AMBIENT_GLOW)) { store.set(Notifications.AMBIENT_GLOW.key, it) }
            }
        }
        item("cards") {
            Group(title = "Notification cards") {
                SettingSlider("Card width", Notifications.CARD_WIDTH, values, store)
                SettingSlider("Card size", Notifications.CARD_SCALE, values, store)
                ToggleRow("Pin and swipe-left options", on(Notifications.SWIPE_OPTIONS)) { store.set(Notifications.SWIPE_OPTIONS.key, it) }
            }
        }
        item("stack") {
            Group(title = "Notification stack") {
                ToggleRow("Stacked display", on(Notifications.STACK)) { store.set(Notifications.STACK.key, it) }
                if (on(Notifications.STACK)) {
                    Divider()
                    ChoiceRow("Stack range", listOf("Same group", "Same app", "All notifications"), number(Notifications.GROUPING)) {
                        store.set(Notifications.GROUPING.key, it)
                    }
                    SettingSlider("Layers shown behind", Notifications.STACK_LAYERS, values, store, " layers")
                    SettingSlider("Exposed height per layer", Notifications.STACK_PEEK, values, store, " dp")
                    SettingSlider("Width of each layer behind", Notifications.STACK_SCALE, values, store)
                    SettingSlider("Dim per layer behind", Notifications.STACK_DIM, values, store)
                }
            }
        }
        item("badge") {
            val badge = NotificationBadgeSpec.decode(text(Notifications.BADGE))
            BadgeSettings(badge) { store.set(Notifications.BADGE.key, it.encode()) }
        }
        item("sort") {
            Group(title = "Notification sorting") {
                ToggleRow("Sort within group", on(Notifications.SORT_ENABLED)) { store.set(Notifications.SORT_ENABLED.key, it) }
                if (on(Notifications.SORT_ENABLED)) {
                    Divider()
                    val primary = number(Notifications.SORT_PRIMARY)
                    ChoiceRow("Sort by", listOf("Card height", "Time sent", "App name", "Importance"), primary) {
                        store.set(Notifications.SORT_PRIMARY.key, it)
                    }
                    Divider()
                    val orders = listOf(
                        listOf("Height, high to low", "Height, low to high"),
                        listOf("Time, newest first", "Time, oldest first"),
                        listOf("A to Z", "Z to A"),
                        listOf("Importance, high to low", "Importance, low to high"),
                    )
                    ChoiceRow("Sort order", orders[primary], if (on(Notifications.SORT_DESC)) 1 else 0) {
                        store.set(Notifications.SORT_DESC.key, it == 1)
                    }
                    Divider()
                    ToggleRow("Show stackable notifications first", on(Notifications.SORT_STACKED_FIRST)) {
                        store.set(Notifications.SORT_STACKED_FIRST.key, it)
                    }
                    Divider()
                    PriorityAppsRow(text(Notifications.SORT_PRIORITY)) { store.set(Notifications.SORT_PRIORITY.key, it) }
                }
            }
        }
        item("controls") {
            Group(title = "Control center") {
                ToggleRow("Extra dimming in light mode", on(Controls.DIM_LIGHT_ENABLED)) {
                    store.set(Controls.DIM_LIGHT_ENABLED.key, it)
                }
                if (on(Controls.DIM_LIGHT_ENABLED)) {
                    SettingSlider("Dim amount", Controls.DIM_LIGHT, values, store)
                }
            }
        }
        item("reset") {
            Group {
                Item(onClick = { reset = true }) { Text(text = "Restore defaults", kind = CouiTextKind.ItemTitle) }
            }
        }
    }
    if (reset) {
        Dialog(
            title = "Restore defaults",
            message = "Restore appearance, stacking and sorting to default values.",
            onDismissRequest = { reset = false },
            dismissAction = CouiDialogAction("Cancel", { reset = false }),
            confirmAction = CouiDialogAction("Restore", {
                store.resetAppearance()
                reset = false
            }),
        )
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, change: (Boolean) -> Unit) {
    Item(
        onClick = { change(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null, onDragCheckedChange = change) },
    ) { Text(text = title, kind = CouiTextKind.ItemTitle) }
}

@Composable
private fun SettingSlider(
    title: String,
    item: Notifications.IntItem,
    values: Map<String, *>,
    store: FrostSettingsStore,
    suffix: String = "%",
) = NumberSlider(title, item.clamp(values[item.key] as? Int ?: item.default), item.min..item.max, suffix) {
    store.set(item.key, it)
}

@Composable
private fun NumberSlider(title: String, value: Int, range: IntRange, suffix: String, change: (Int) -> Unit) {
    var displayed by remember(value) { mutableStateOf(value) }
    Item(
        summary = {
            Slider(
                value = displayed.toFloat(),
                onValueChange = { displayed = it.roundToInt().coerceIn(range) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = title },
                onValueChangeFinished = { if (displayed != value) change(displayed) },
            )
        },
    ) { Text(text = "$title  $displayed$suffix", kind = CouiTextKind.ItemTitle) }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Item(onClick = onClick, trailing = { Text(text = value, kind = CouiTextKind.ItemValue) }) {
        Text(text = title, kind = CouiTextKind.ItemTitle)
    }
}

@Composable
private fun ChoiceRow(title: String, labels: List<String>, selected: Int, change: (Int) -> Unit) {
    var shown by rememberSaveable { mutableStateOf(false) }
    ValueRow(title, labels[selected]) { shown = true }
    if (shown) {
        Dialog(
            title = title,
            onDismissRequest = { shown = false },
            confirmAction = CouiDialogAction("Close", { shown = false }),
        ) {
            labels.forEachIndexed { index, label ->
                Item(
                    onClick = { change(index); shown = false },
                    trailing = { RadioButton(selected = index == selected) },
                ) { Text(text = label, kind = CouiTextKind.ItemTitle) }
            }
        }
    }
}

@Composable
private fun BadgeSettings(badge: NotificationBadgeSpec, change: (NotificationBadgeSpec) -> Unit) {
    Group(title = "Count badge") {
        ToggleRow("Show count badge", badge.enabled) { change(badge.copy(enabled = it)) }
        if (badge.enabled) {
            Divider()
            ChoiceRow("Number meaning", listOf("All notifications in group", "Hidden notifications in group"), badge.mode) { change(badge.copy(mode = it)) }
            Divider()
            ChoiceRow("Badge position", listOf("Top left", "Top right", "Bottom left", "Bottom right"), badge.corner) { change(badge.copy(corner = it)) }
            NumberSlider("Text size", badge.textSp, NotificationBadgeSpec.TEXT_SP_RANGE, " sp") { change(badge.copy(textSp = it)) }
            NumberSlider("Padding", badge.paddingDp, NotificationBadgeSpec.PADDING_DP_RANGE, " dp") { change(badge.copy(paddingDp = it)) }
            NumberSlider("Badge corner radius", badge.radiusDp, NotificationBadgeSpec.RADIUS_DP_RANGE, " dp") { change(badge.copy(radiusDp = it)) }
            NumberSlider("Margin", badge.marginDp, NotificationBadgeSpec.MARGIN_DP_RANGE, " dp") { change(badge.copy(marginDp = it)) }
            BadgeColor("Background color", badge.bgColor) { change(badge.copy(bgColor = it)) }
            BadgeColor("Text color", badge.textColor) { change(badge.copy(textColor = it)) }
        }
    }
}

@Composable
private fun BadgeColor(title: String, color: Int, change: (Int) -> Unit) {
    val hex = String.format(Locale.ROOT, "#%08X", color)
    var input by remember { mutableStateOf<TextFieldValue?>(null) }
    ValueRow(title, hex) { input = TextFieldValue(hex) }
    val draft = input ?: return
    val parsed = runCatching { android.graphics.Color.parseColor(draft.text.trim()) }.getOrNull()
    InputDialog(
        title = title,
        value = draft,
        onValueChange = { input = it },
        onDismissRequest = { input = null },
        placeholder = "#AARRGGBB",
        clearContentDescription = "Clear",
        dismissAction = CouiDialogAction("Cancel", { input = null }),
        confirmAction = CouiDialogAction("Save", {
            if (parsed != null) change(parsed)
            input = null
        }, enabled = parsed != null),
    )
}

@Composable
private fun PriorityAppsRow(stored: String, change: (String) -> Unit) {
    val chosen = stored.lines().filter { it.isNotBlank() }
    var draft by remember { mutableStateOf<List<String>?>(null) }
    ValueRow("Choose priority apps", if (chosen.isEmpty()) "None selected" else "${chosen.size} selected") { draft = chosen }
    val selection = draft ?: return
    val context = LocalContext.current
    val apps by produceState<List<Pair<String, String>>?>(null) {
        value = withContext(Dispatchers.IO) {
            val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            context.packageManager.queryIntentActivities(launchable, PackageManager.ResolveInfoFlags.of(0))
                .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
                .filter { it.first != context.packageName }
                .distinctBy { it.first }
                .sortedWith(compareBy({ it.second.lowercase(Locale.ROOT) }, { it.first }))
        }
    }
    Dialog(
        title = "Choose priority apps",
        onDismissRequest = { draft = null },
        dismissAction = CouiDialogAction("Cancel", { draft = null }),
        confirmAction = CouiDialogAction("Save", {
            change(selection.joinToString("\n"))
            draft = null
        }),
    ) {
        val available = apps
        if (available == null) {
            Footer(text = "Loading apps")
        } else {
            val labels = available.toMap()
            val ordered = selection.map { it to (labels[it] ?: it) } + available.filter { it.first !in selection }
            for ((packageName, label) in ordered) {
                val checked = packageName in selection
                Item(
                    onClick = { draft = if (checked) selection - packageName else selection + packageName },
                    trailing = { Checkbox(checked = checked) },
                ) { Text(text = label, kind = CouiTextKind.ItemTitle) }
            }
        }
    }
}
