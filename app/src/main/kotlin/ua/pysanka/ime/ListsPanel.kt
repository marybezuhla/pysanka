package ua.pysanka.ime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.subtypeManager
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * Pysanka: lists, to-dos and the date in place of the keys (the checklist button on the bar above
 * the keyboard). Six tiles; a tap does it and brings the keys back. See [Lists].
 */
@Composable
fun ListsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    fun close() {
        keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
    }
    fun locale() = runCatching { context.subtypeManager().value.activeSubtype.primaryLocale.base }.getOrDefault(java.util.Locale.getDefault())
    val app = editorInstance.activeInfo.packageName

    val tiles = listOf(
        Triple(Icons.AutoMirrored.Filled.FormatListBulleted, R.string.pysanka__lists__bullet) { Lists.insert(Lists.Kind.BULLET, app) },
        Triple(Icons.Default.FormatListNumbered, R.string.pysanka__lists__number) { Lists.insert(Lists.Kind.NUMBER, app) },
        Triple(Icons.Default.CheckBox, R.string.pysanka__lists__todo) { Lists.insert(Lists.Kind.TODO, app) },
        Triple(Icons.Default.Today, R.string.pysanka__lists__date) { Lists.type(Lists.now(Lists.When.DATE, locale())) },
        Triple(Icons.Default.Schedule, R.string.pysanka__lists__time) { Lists.type(Lists.now(Lists.When.TIME, locale())) },
        Triple(Icons.Default.DateRange, R.string.pysanka__lists__date_time) { Lists.type(Lists.now(Lists.When.DATE_TIME, locale())) },
    )

    SnyggColumn(modifier = modifier.fillMaxWidth().height(FlorisImeSizing.imeUiHeight())) {
        val headerHeight = FlorisImeSizing.smartbarHeight
        SnyggRow(
            FlorisImeUi.ClipboardHeader.elementName,
            modifier = Modifier.fillMaxWidth().height(headerHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val size = Modifier.sizeIn(maxHeight = headerHeight).aspectRatio(1f)
            SnyggIcon(imageVector = Icons.Default.Checklist, modifier = size.padding(10.dp))
            SnyggText(
                FlorisImeUi.ClipboardHeaderText.elementName,
                modifier = Modifier.weight(1f),
                text = stringRes(R.string.pysanka__lists__title),
            )
            SnyggIconButton(FlorisImeUi.ClipboardHeaderButton.elementName, onClick = { close() }, modifier = size) {
                SnyggIcon(imageVector = Icons.Default.Close, contentDescription = stringRes(R.string.pysanka__ocr__close))
            }
        }
        SnyggBox(FlorisImeUi.ClipboardContent.elementName, modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Three across, like the AI panel's tiles
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(tiles) { (icon, label, action) ->
                    Tile(icon, stringRes(label)) {
                        action()
                        close()
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, onClick: () -> Unit) {
    SnyggBox(
        FlorisImeUi.SmartbarActionTile.elementName,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        clickAndSemanticsModifier = Modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SnyggIcon(imageVector = icon)
            SnyggText(FlorisImeUi.SmartbarActionTileText.elementName, text = label)
        }
    }
}
