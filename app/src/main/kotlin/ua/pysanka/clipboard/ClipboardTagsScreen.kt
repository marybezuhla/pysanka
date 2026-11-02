package ua.pysanka.clipboard

import androidx.compose.material.icons.filled.MoreVert
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes

/** Pysanka: opens the tag settings from the keyboard (hides the keyboard first). */
fun openTagSettings(context: Context) {
    FlorisImeService.hideUi()
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("ui://pysanka/settings/clipboard/tags")).apply {
        setPackage(context.packageName)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    runCatching { context.startActivity(intent) }
}

/**
 * Pysanka: the owner's clipboard tags: add, rename, recolour, reorder, delete. Deleting a tag
 * keeps its items, only without the tag.
 */
@Composable
fun ClipboardTagsScreen() = FlorisScreen {
    title = stringRes(R.string.pysanka__tag__screen_title)
    previewFieldVisible = false

    content {
        val context = LocalContext.current
        val clipboardManager by context.clipboardManager()
        val tags by clipboardManager.tagsFlow.collectAsState()

        // The tag being edited: null = none, id 0 = a new one
        var editingId by remember { mutableStateOf<Long?>(null) }
        var name by remember { mutableStateOf("") }
        var color by remember { mutableStateOf(0) }
        var confirmDelete by remember { mutableStateOf<Long?>(null) }

        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringRes(R.string.pysanka__tag__screen_hint), style = MaterialTheme.typography.bodyMedium)

            if (tags.isEmpty() && editingId == null) {
                Text(stringRes(R.string.pysanka__tag__none_yet), style = MaterialTheme.typography.bodyLarge)
            }

            for ((i, tag) in tags.withIndex()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(16.dp).background(TagColors.color(tag.color), CircleShape))
                    Text(tag.name, modifier = Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
                    // Each button names its tag when read out; edit and delete are in a menu, so the
                    // name keeps its room with large text
                    IconButton(enabled = i > 0, onClick = { clipboardManager.moveTag(tag.id, up = true) }) {
                        Icon(Icons.Default.KeyboardArrowUp, stringRes(R.string.pysanka__tag__move_up_named, "name" to tag.name))
                    }
                    IconButton(enabled = i < tags.lastIndex, onClick = { clipboardManager.moveTag(tag.id, up = false) }) {
                        Icon(Icons.Default.KeyboardArrowDown, stringRes(R.string.pysanka__tag__move_down_named, "name" to tag.name))
                    }
                    Box {
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, stringRes(R.string.pysanka__tag__actions_named, "name" to tag.name))
                        }
                        androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringRes(R.string.pysanka__tag__edit)) },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menu = false; editingId = tag.id; name = tag.name; color = tag.color },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringRes(R.string.pysanka__tag__delete)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menu = false; confirmDelete = tag.id },
                            )
                        }
                    }
                }
                if (confirmDelete == tag.id) {
                    Text(stringRes(R.string.pysanka__tag__delete_confirm).replace("{name}", tag.name))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { clipboardManager.deleteTag(tag.id); confirmDelete = null }) {
                            Text(stringRes(R.string.pysanka__tag__delete))
                        }
                        TextButton(onClick = { confirmDelete = null }) { Text(stringRes(R.string.pysanka__tag__cancel)) }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (editingId == null) {
                OutlinedButton(onClick = { editingId = 0; name = ""; color = tags.size % TagColors.colors.size }) {
                    Text(stringRes(R.string.pysanka__tag__add))
                }
            } else {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    label = { Text(stringRes(R.string.pysanka__tag__name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringRes(R.string.pysanka__tag__color), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TagColors.colors.forEachIndexed { i, c ->
                        val label = stringRes(TagColors.names[i])
                        Box(
                            Modifier
                                .size(48.dp)
                                .background(c, CircleShape)
                                .then(
                                    if (color == i) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                                )
                                .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { color = i }
                                .semantics { contentDescription = label; selected = color == i },
                        )
                    }
                }
                Text(stringRes(TagColors.names[color]), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = {
                            val id = editingId
                            if (id == 0L) clipboardManager.addTag(name, color) else if (id != null) clipboardManager.updateTag(id, name, color)
                            editingId = null
                        },
                    ) { Text(stringRes(R.string.pysanka__tag__save)) }
                    TextButton(onClick = { editingId = null }) { Text(stringRes(R.string.pysanka__tag__cancel)) }
                }
            }
        }
    }
}
