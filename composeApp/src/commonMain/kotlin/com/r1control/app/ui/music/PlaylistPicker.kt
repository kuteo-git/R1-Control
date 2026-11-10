package com.r1control.app.ui.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.PlaylistId
import com.r1control.app.ui.components.NameDialog
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens

/**
 * The "add to playlist" sheet: the playlists, newest first, then "New
 * playlist", which asks for a name and creates the playlist holding the songs.
 * A playlist that already holds every song being added wears a check and still
 * closes the sheet when picked: the add is a no-op, said rather than hidden.
 *
 * R1 has no popup ban (that rule is mytube's, for sampled glass), so this is
 * Material's own sheet on the card colour. Nothing is drawn for [Picker.Closed].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPicker(picker: Picker, onPick: (PlaylistId) -> Unit, onPickNew: (String) -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    var naming by remember { mutableStateOf(false) }
    if (picker !is Picker.Open) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Tokens.CardRaised,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                strings.addToPlaylist,
                modifier = Modifier.padding(horizontal = Space.Gutter, vertical = Space.Small),
                style = MaterialTheme.typography.titleLarge,
            )
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item(key = "new") {
                    PickerLine(R1Icons.Add, strings.newPlaylist, "", checked = false) { naming = true }
                }
                items(picker.playlists, key = { "playlist:" + it.id.value }) { row ->
                    PickerLine(
                        icon = R1Icons.MusicNote,
                        title = if (row.hasName) row.name else strings.untitledPlaylist,
                        subtitle = strings.songCount(row.count),
                        checked = row.hasAll,
                        checkedDescription = strings.alreadyInPlaylist,
                    ) { onPick(row.id) }
                }
            }
        }
    }
    if (naming) {
        NameDialog(
            title = strings.newPlaylist, initial = "", hint = strings.playlistName,
            confirmText = strings.create, cancelText = strings.cancel,
            onConfirm = { naming = false; onPickNew(it) },
            onCancel = { naming = false },
        )
    }
}

@Composable
private fun PickerLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    checkedDescription: String = "",
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (checked) stateDescription = checkedDescription }
            .padding(horizontal = Space.Gutter, vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        R1Icon(icon, null, tint = Tokens.OnGroundMuted, size = 24.dp)
        Column(Modifier.weight(1f).padding(horizontal = Space.Large), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (checked) R1Icon(R1Icons.Check, null, tint = Tokens.Accent)
    }
}
