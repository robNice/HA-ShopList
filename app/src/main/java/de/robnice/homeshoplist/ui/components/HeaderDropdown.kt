package de.robnice.homeshoplist.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Compact dropdown for the top bar: the current value as text with a triangle,
 * tapping opens a menu. Keeps the height of plain text so the header does not grow.
 */
@Composable
fun <T> HeaderDropdown(
    label: String,
    items: List<T>,
    itemLabel: (T) -> String,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    /** Shown before the text so it is clear what the dropdown selects. */
    leadingIcon: ImageVector? = null,
    /** Called each time the menu opens, e.g. to refresh the items. */
    onOpen: () -> Unit = {},
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    fontWeight: FontWeight? = FontWeight.SemiBold,
    color: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true
) {
    val contentColor = if (enabled) color else color.copy(alpha = 0.38f)
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(
                    enabled = enabled,
                    onClickLabel = onClickLabel,
                    role = Role.DropdownList
                ) {
                    expanded = true
                    onOpen()
                }
                .padding(start = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            // Long names end with an ellipsis instead of pushing the triangle out
            Text(
                text = label,
                modifier = Modifier.weight(1f, fill = false),
                style = textStyle,
                fontWeight = fontWeight,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }

        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                val selected = isSelected(item)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = itemLabel(item),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selected) FontWeight.SemiBold else null
                        )
                    },
                    leadingIcon = {
                        if (selected) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        if (!selected) {
                            onSelect(item)
                        }
                    }
                )
            }
        }
    }
}
