package de.robnice.homeasssistant_shoppinglist.ui.components
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.*
import de.robnice.homeasssistant_shoppinglist.R
import de.robnice.homeasssistant_shoppinglist.model.ShoppingList
import de.robnice.homeasssistant_shoppinglist.ui.util.t

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDropdown(
    lists: List<ShoppingList>,
    selected: ShoppingList?,
    onSelect: (ShoppingList) -> Unit
) {

    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {

        TextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(t(R.string.list)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            },
            modifier = Modifier.menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.name) },
                    onClick = {
                        expanded = false
                        onSelect(list)
                    }
                )
            }
        }
    }
}
