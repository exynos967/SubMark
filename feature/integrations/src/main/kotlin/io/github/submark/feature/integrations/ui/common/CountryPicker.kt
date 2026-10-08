package io.github.submark.feature.integrations.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.submark.core.ui.component.SearchField
import io.github.submark.feature.integrations.R
import io.github.submark.feature.integrations.data.CountryCatalog

/** Bottom sheet picking one storefront (region switch). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.integrations_region_picker_title), style = MaterialTheme.typography.titleMedium)
            SearchField(query = query, onQueryChange = { query = it }, modifier = Modifier.padding(vertical = 8.dp))
            val countries = CountryCatalog.all().filter {
                query.isBlank() || it.displayName.contains(query.trim(), true) || it.code.contains(query.trim(), true)
            }
            LazyColumn {
                items(countries, key = { it.code }) { country ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(country.code) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = country.code.equals(selectedCode, true), onClick = null)
                        Text(
                            "${country.displayName} (${country.code})",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Bottom sheet picking several storefronts (price-monitor region list). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiCountryPickerSheet(
    selectedCodes: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.integrations_region_picker_title), style = MaterialTheme.typography.titleMedium)
            SearchField(query = query, onQueryChange = { query = it }, modifier = Modifier.padding(vertical = 8.dp))
            val countries = CountryCatalog.all().filter {
                query.isBlank() || it.displayName.contains(query.trim(), true) || it.code.contains(query.trim(), true)
            }
            LazyColumn {
                items(countries, key = { it.code }) { country ->
                    val selected = selectedCodes.any { it.equals(country.code, true) }
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(country.code) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = selected, onCheckedChange = null)
                        Text(
                            "${country.displayName} (${country.code})",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
