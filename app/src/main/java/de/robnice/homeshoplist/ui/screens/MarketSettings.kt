package de.robnice.homeshoplist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.robnice.homeshoplist.R
import de.robnice.homeshoplist.model.ShoppingMarket
import de.robnice.homeshoplist.model.ShoppingMarkets
import de.robnice.homeshoplist.ui.util.t

/** Dropdown for the market being edited plus add, rename and delete buttons. */
@Composable
internal fun MarketSelectorRow(
    markets: List<ShoppingMarket>,
    selected: ShoppingMarket,
    onSelect: (ShoppingMarket) -> Unit,
    onAdd: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarketDropdown(
            markets = markets,
            selected = selected,
            label = t(R.string.market_label),
            onSelect = onSelect,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onAdd, modifier = Modifier.testTag("market_add")) {
            Icon(Icons.Default.Add, contentDescription = t(R.string.market_add))
        }
        IconButton(onClick = onRename) {
            Icon(Icons.Default.Edit, contentDescription = t(R.string.market_rename))
        }
        // The last remaining market can never be deleted
        IconButton(onClick = onDelete, enabled = markets.size > 1) {
            Icon(Icons.Default.DeleteOutline, contentDescription = t(R.string.market_delete))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketDropdown(
    markets: List<ShoppingMarket>,
    selected: ShoppingMarket,
    label: String,
    onSelect: (ShoppingMarket) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
        )

        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            markets.forEach { market ->
                DropdownMenuItem(
                    text = { Text(market.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        expanded = false
                        onSelect(market)
                    }
                )
            }
        }
    }
}

@Composable
private fun MarketNameField(
    name: String,
    onNameChange: (String) -> Unit,
    error: ShoppingMarkets.NameError?
) {
    OutlinedTextField(
        value = name,
        onValueChange = { onNameChange(it.take(ShoppingMarkets.MAX_NAME_LENGTH)) },
        label = { Text(t(R.string.market_name_label)) },
        singleLine = true,
        // An empty name only disables the button; a duplicate needs an explanation
        isError = error == ShoppingMarkets.NameError.DUPLICATE,
        supportingText = if (error == ShoppingMarkets.NameError.DUPLICATE) {
            { Text(t(R.string.market_name_duplicate)) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Asks for the name and whether the new market starts empty (default order)
 * or as a copy of an existing market.
 */
@Composable
internal fun AddMarketDialog(
    markets: List<ShoppingMarket>,
    initialTemplate: ShoppingMarket,
    onDismiss: () -> Unit,
    onConfirm: (name: String, templateId: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var copyFromTemplate by remember { mutableStateOf(false) }
    var template by remember { mutableStateOf(initialTemplate) }
    val error = ShoppingMarkets.validateName(name, markets)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(R.string.market_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarketNameField(name = name, onNameChange = { name = it }, error = error)

                Column(modifier = Modifier.selectableGroup()) {
                    TemplateOption(
                        text = t(R.string.market_template_new),
                        selected = !copyFromTemplate,
                        onClick = { copyFromTemplate = false }
                    )
                    TemplateOption(
                        text = t(R.string.market_template_copy),
                        selected = copyFromTemplate,
                        onClick = { copyFromTemplate = true }
                    )
                }

                MarketDropdown(
                    markets = markets,
                    selected = template,
                    label = t(R.string.market_template_copy),
                    onSelect = { template = it },
                    enabled = copyFromTemplate
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, if (copyFromTemplate) template.id else null) },
                enabled = error == null
            ) {
                Text(t(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t(R.string.clear_completed_confirm_btn_cancel))
            }
        }
    )
}

@Composable
private fun TemplateOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = text)
    }
}

@Composable
internal fun RenameMarketDialog(
    market: ShoppingMarket,
    markets: List<ShoppingMarket>,
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit
) {
    var name by remember(market.id) { mutableStateOf(market.name) }
    val error = ShoppingMarkets.validateName(name, markets, excludeId = market.id)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(R.string.market_rename)) },
        text = { MarketNameField(name = name, onNameChange = { name = it }, error = error) },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = error == null) {
                Text(t(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t(R.string.clear_completed_confirm_btn_cancel))
            }
        }
    )
}

@Composable
internal fun DeleteMarketDialog(
    market: ShoppingMarket,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(R.string.market_delete)) },
        text = { Text(stringResource(R.string.market_delete_confirm_text, market.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(t(R.string.clear_completed_confirm_btn_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t(R.string.clear_completed_confirm_btn_cancel))
            }
        }
    )
}
