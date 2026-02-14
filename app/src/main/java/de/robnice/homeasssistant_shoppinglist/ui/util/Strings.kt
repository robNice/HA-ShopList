package de.robnice.homeasssistant_shoppinglist.ui.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun t(@StringRes id: Int): String {
    return stringResource(id)
}
