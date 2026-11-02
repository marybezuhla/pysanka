package ua.pysanka.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R

/**
 * Pysanka: the icon's pysanka, very faint, tilted and cut off by the screen's lower corner, behind
 * the settings and the welcome screen. Only decoration: not read aloud, never over the content.
 */
@Composable
fun BoxScope.PysankaBackdrop() {
    Image(
        painter = painterResource(R.drawable.ic_app_icon_monochrome),
        contentDescription = null,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = 170.dp, y = 130.dp)
            .size(580.dp)
            .rotate(-20f)
            .alpha(0.06f),
    )
}
