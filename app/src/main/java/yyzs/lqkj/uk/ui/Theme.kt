package yyzs.lqkj.uk.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yyzs.lqkj.uk.Lang

val Accent = Color(0xFF6A5AE0)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            secondary = Color(0xFF4F8DF7),
            surface = Color.White,
            background = Color(0xFFF4F4F8),
        ),
        content = content,
    )
}

@Composable
fun LanguageDropdown(
    current: Lang,
    options: List<Lang>,
    onSelect: (Lang) -> Unit,
    color: Color = Color.Unspecified,
    /** Chip-sized: smaller text and no minimum touch height. */
    compact: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = { open = true },
            contentPadding = if (compact) PaddingValues(start = 4.dp, end = 6.dp) else ButtonDefaults.TextButtonContentPadding,
            modifier = if (compact) Modifier.height(32.dp) else Modifier,
        ) {
            Text(
                current.label, color = color,
                style = if (compact) MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp) else MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = color, modifier = if (compact) Modifier.size(18.dp) else Modifier)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (lang in options) {
                DropdownMenuItem(text = { Text(lang.label) }, onClick = { open = false; onSelect(lang) })
            }
        }
    }
}
