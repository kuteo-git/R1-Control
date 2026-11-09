package com.r1control.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.LanguageChoice
import com.r1control.app.ui.AppViewModel
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.Language
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.i18n.deviceLanguage
import com.r1control.app.ui.i18n.languageChoiceName
import com.r1control.app.ui.i18n.ownName
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Settings, Language. Three rows, the chosen one checked: "Follow the phone"
 * (in the current language, with the phone's own language named under it),
 * "Tiếng Việt" and "English", each language in its own words. A tap applies at
 * once: the choice is the state the app reads its words from, so this page
 * redraws in the new language while it is open. It has no view model of its
 * own: the choice is the app's.
 */
@Composable
fun LanguageScreen(app: AppViewModel) {
    val choice by app.language.collectAsState()
    val device = remember { deviceLanguage() }
    LanguageContent(choice, device, app::chooseLanguage)
}

@Composable
fun LanguageContent(choice: LanguageChoice, device: Language, onChoose: (LanguageChoice) -> Unit) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxSize()) {
        LanguageChoice.entries.forEach { entry ->
            val subtitle = if (entry == LanguageChoice.FollowPhone) strings.languageFollowPhoneNow(device.ownName) else ""
            ChoiceRow(strings.languageChoiceName(entry), subtitle, entry == choice) { onChoose(entry) }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = Space.Gutter, vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // A change of shape, not of shade: the chosen row has a check, the others have nothing.
        if (selected) R1Icon(R1Icons.Check, null, tint = Tokens.Accent) else Box(Modifier.size(24.dp))
    }
}

@Composable
private fun PreviewHost(strings: Strings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground)) { content() } }
    }
}

@Preview
@Composable
private fun LanguageFollowPreview() = PreviewHost(EnglishStrings) { LanguageContent(LanguageChoice.FollowPhone, Language.Vietnamese, {}) }

@Preview
@Composable
private fun LanguageVietnameseChosenPreview() = PreviewHost(VietnameseStrings) { LanguageContent(LanguageChoice.Vietnamese, Language.English, {}) }

@Preview
@Composable
private fun LanguageEnglishChosenPreview() = PreviewHost(EnglishStrings) { LanguageContent(LanguageChoice.English, Language.Vietnamese, {}) }
