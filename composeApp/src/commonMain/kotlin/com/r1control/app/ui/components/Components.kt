package com.r1control.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.r1control.app.domain.model.cleanPlaylistName
import com.r1control.app.ui.i18n.describe
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.Radius
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens

/** A glyph in a colour. [description] is what a screen reader says, or null when text beside it already says it. */
@Composable
fun R1Icon(
    icon: ImageVector,
    description: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    size: Dp = 24.dp,
) {
    androidx.compose.material3.Icon(icon, contentDescription = description, modifier = modifier.size(size), tint = tint)
}

/** A 48dp round tap target around an icon. */
@Composable
fun IconTap(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    background: Color = Color.Transparent,
    iconSize: Dp = 24.dp,
    box: Dp = 48.dp,
    disabledTint: Color = tint.copy(alpha = 0.38f),
) {
    Box(
        modifier = modifier
            .size(box)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        R1Icon(icon, description, tint = if (enabled) tint else disabledTint, size = iconSize)
    }
}

/** The title bar: an optional back arrow, a title with a small line under it, and actions on the right. */
@Composable
fun ScreenTopBar(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    backDescription: String? = null,
    onBack: () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (backDescription != null) {
            // A new arrow for each page: one kept across pages kept the last page's pressed look.
            key(title) { IconTap(R1Icons.Back, backDescription, onBack) }
        } else {
            Spacer(Modifier.width(Space.Small))
        }
        Column(Modifier.weight(1f).padding(start = Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        actions()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.XLarge),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            val content = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    R1Icon(icon, null, tint = content, size = 20.dp)
                    Spacer(Modifier.width(Space.Small))
                }
                Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
            }
        }
    }
}

/** A quieter pill: the card colour, for the second action on a screen. */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val content = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.XLarge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            R1Icon(icon, null, tint = content, size = 20.dp)
            Spacer(Modifier.width(Space.Small))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

/** A pill with an outline and no fill, like YouTube Music's "Play all". */
@Composable
fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.Large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            R1Icon(icon, null, size = 18.dp)
            Spacer(Modifier.width(Space.Hair))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** A text field on a rounded card, whole surface tappable. [trailing] sits inside its right edge. */
@Composable
fun R1TextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onDone: (() -> Unit)? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(Radius.Field))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = Space.Large, end = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The field keeps its own TextFieldValue: a String round trip through a ViewModel drops
        // the IME's composition range, which is what a Telex keyboard is assembling a diacritic
        // in, and puts the caret back at 0. Only a change made elsewhere (a field cleared after
        // sending, a recent search loaded) replaces it. [value] can arrive a keystroke or two
        // behind the field, so a text this field reported itself is an echo, never a change.
        var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
        val echoes = remember { ArrayDeque<String>() }
        LaunchedEffect(value) {
            val echo = echoes.indexOf(value)
            if (echo >= 0) {
                repeat(echo + 1) { echoes.removeFirst() }
            } else if (value != field.text) {
                echoes.clear()
                field = TextFieldValue(value, TextRange(value.length))
            }
        }
        Box(Modifier.weight(1f).padding(vertical = Space.Medium)) {
            if (field.text.isEmpty()) {
                Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            BasicTextField(
                value = field,
                onValueChange = {
                    val changed = it.text != field.text
                    field = it
                    if (changed) {
                        echoes.addLast(it.text)
                        if (echoes.size > MAX_ECHOES) echoes.removeFirst()
                        onValueChange(it.text)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .let { m -> if (focusRequester != null) m.focusRequester(focusRequester) else m },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = singleLine,
                maxLines = maxLines,
                keyboardOptions = keyboardOptions,
                keyboardActions = KeyboardActions(onAny = { onDone?.invoke() }),
            )
        }
        trailing()
    }
}

private const val MAX_ECHOES = 64

/** A tap that nothing under it took closes the keyboard. */
@Composable
fun Modifier.closesKeyboardOnTap(): Modifier {
    val focus = LocalFocusManager.current
    return pointerInput(focus) { detectTapGestures { focus.clearFocus() } }
}

/** A problem as a quiet line, not a crash. [action] is an optional text button, such as Try again. */
@Composable
fun ProblemBanner(text: String, modifier: Modifier = Modifier, actionText: String = "", onAction: () -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.Row))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = Space.Medium, vertical = Space.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        R1Icon(R1Icons.Warning, null, tint = MaterialTheme.colorScheme.error, size = 20.dp)
        Text(
            text,
            modifier = Modifier.weight(1f).padding(horizontal = Space.Medium),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        if (actionText.isNotEmpty()) {
            Text(
                actionText,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onAction)
                    .wrapContentHeight()
                    .padding(horizontal = Space.Small),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = Space.Large, bottom = Space.Small),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(Space.XLarge), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun FailedBox(message: String, retryText: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(Space.XLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.Large),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        TonalButton(retryText, onRetry)
    }
}

/** A cover image on a card-coloured tile with a note on it while it loads or when there is none. */
@Composable
fun Cover(url: String, size: Dp, modifier: Modifier = Modifier, radius: Dp = Radius.Cover) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(radius)).background(Tokens.CardRaised),
        contentAlignment = Alignment.Center,
    ) {
        R1Icon(R1Icons.MusicNote, null, tint = Tokens.OnGroundMuted, size = size / 2)
        if (url.isNotEmpty()) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** One row of a settings list: a glyph, a title, an optional line under it and a chevron. */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.Gutter, vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        R1Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 26.dp)
        Column(Modifier.weight(1f).padding(horizontal = Space.Large), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        R1Icon(R1Icons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A title (and optional line) with a switch on the right. The whole row toggles. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = Space.Gutter, vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        R1Switch(checked, onChange, enabled)
    }
}

@Composable
fun R1Switch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

/** Where the content column of a page sits: the gutter on both sides. */
val PageGutter = PaddingValues(horizontal = Space.Gutter)


/**
 * A yes/no question over the page, drawn as the app's own card with its own
 * pills. A dialog is a window of its own with no `Surface` above it, so every
 * colour here is explicit.
 */
@Composable
fun ConfirmDialog(text: String, confirmText: String, cancelText: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(Tokens.CardRaised).padding(Space.XLarge),
            verticalArrangement = Arrangement.spacedBy(Space.XLarge),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
                TonalButton(cancelText, onCancel, Modifier.weight(1f))
                PrimaryButton(confirmText, onConfirm, Modifier.weight(1f))
            }
        }
    }
}

/**
 * A name asked over the page: the app's card with a field and two pills. The
 * field is focused on open with the caret at the end of [initial] (a field
 * focuses at 0 otherwise). Confirm is disabled while the cleaned text is empty;
 * it hands back the raw text, and the caller cleans it.
 */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    hint: String,
    confirmText: String,
    cancelText: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val canConfirm = cleanPlaylistName(text).isNotEmpty()
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(Tokens.CardRaised).padding(Space.XLarge),
            verticalArrangement = Arrangement.spacedBy(Space.Large),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            R1TextField(
                value = text,
                onValueChange = { text = it },
                hint = hint,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onDone = if (canConfirm) ({ onConfirm(text) }) else null,
                focusRequester = focus,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
                TonalButton(cancelText, onCancel, Modifier.weight(1f))
                PrimaryButton(confirmText, { onConfirm(text) }, Modifier.weight(1f), enabled = canConfirm)
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** A quiet text button, 48 dp tall, for an action that is named rather than drawn. */
@Composable
fun TextAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.Medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

/** Shows a [Loadable]: a spinner, a failure with Try again, or [content]. */
@Composable
fun <T> LoadableContent(
    loadable: com.r1control.app.ui.Loadable<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    val strings = com.r1control.app.ui.i18n.LocalStrings.current
    when (loadable) {
        com.r1control.app.ui.Loadable.Loading -> LoadingBox()
        is com.r1control.app.ui.Loadable.Failed -> FailedBox(strings.describe(loadable.error), strings.tryAgain, onRetry)
        is com.r1control.app.ui.Loadable.Loaded -> content(loadable.value)
    }
}

/**
 * A slider with a round thumb on a thin track, as in Google Home. Material's own
 * is a tall bar on a thick track, which is not the look this app is drawn from.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun R1Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    steps: Int = 0,
    enabled: Boolean = true,
    active: Color = MaterialTheme.colorScheme.primary,
    inactive: Color = Tokens.CardRaised,
    description: String = "",
    valueText: String = "",
) {
    val alpha = if (enabled) 1f else 0.5f
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        // What a screen reader says: "60 Hz, +10 dB" rather than "slider, 1000.0".
        modifier = modifier.semantics {
            if (description.isNotEmpty()) contentDescription = description
            if (valueText.isNotEmpty()) stateDescription = valueText
        },
        thumb = {
            Box(Modifier.size(20.dp).clip(CircleShape).background(active.copy(alpha = alpha)))
        },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span <= 0f) 0f else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(inactive)) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(active.copy(alpha = alpha)))
            }
        },
    )
}
