package io.github.submark.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.submark.core.model.Currency
import io.github.submark.core.ui.R

/** Sections shown by [CurrencyPickerSheet]; pure so it can be tested. */
data class CurrencySections(val default: Currency?, val builtIn: List<Currency>, val custom: List<Currency>)

fun currencySections(currencies: List<Currency>, query: String, defaultCode: String?): CurrencySections {
    val q = query.trim()
    val matching = currencies.filter {
        q.isEmpty() || it.code.contains(q, true) || it.name.contains(q, true) || it.symbol.contains(q, true)
    }
    val default = matching.firstOrNull { it.code == defaultCode }
    val rest = matching.filter { it.code != defaultCode }.sortedWith(compareBy({ it.sortOrder }, { it.code }))
    return CurrencySections(default, rest.filterNot { it.isCustom }, rest.filter { it.isCustom })
}

/**
 * Bottom sheet listing currencies with search. Groups: default currency (if [defaultCode] given),
 * built-in, custom. The caller decides which currencies to pass (e.g. only enabled ones).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerSheet(
    currencies: List<Currency>,
    selectedCode: String?,
    onSelect: (Currency) -> Unit,
    onDismiss: () -> Unit,
    defaultCode: String? = null,
    title: String = stringResource(R.string.ui_currency_picker_title),
) {
    var query by rememberSaveable { mutableStateOf("") }
    val sections = remember(currencies, query, defaultCode) { currencySections(currencies, query, defaultCode) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        SearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.ui_currency_search),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            sections.default?.let { c ->
                item(key = "h_default") { GroupHeader(stringResource(R.string.ui_currency_group_default)) }
                item(key = "d_${c.code}") { CurrencyRow(c, c.code == selectedCode) { onSelect(c) } }
            }
            if (sections.builtIn.isNotEmpty()) {
                item(key = "h_builtin") { GroupHeader(stringResource(R.string.ui_currency_group_all)) }
                items(sections.builtIn, key = { "b_${it.code}" }) { c -> CurrencyRow(c, c.code == selectedCode) { onSelect(c) } }
            }
            if (sections.custom.isNotEmpty()) {
                item(key = "h_custom") { GroupHeader(stringResource(R.string.ui_currency_group_custom)) }
                items(sections.custom, key = { "c_${it.code}" }) { c -> CurrencyRow(c, c.code == selectedCode) { onSelect(c) } }
            }
            if (sections.default == null && sections.builtIn.isEmpty() && sections.custom.isEmpty()) {
                item(key = "empty") { EmptyState(title = stringResource(R.string.ui_search_no_results), icon = null) }
            }
            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun CurrencyRow(currency: Currency, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            currency.symbol,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(48.dp),
            maxLines = 1,
        )
        Text(currency.code, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp))
        Text(
            currency.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (selected) Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.ui_selected), tint = MaterialTheme.colorScheme.primary)
    }
}
