package io.github.submark.feature.money.ui.paymentmethod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.repository.PaymentMethodRepository
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.model.PaymentMethod
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.money.R
import io.github.submark.feature.money.ui.common.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentMethodForm(
    val id: String? = null,
    val name: String = "",
    val iconValue: String? = null,
    val saving: Boolean = false,
    val error: UiText? = null,
)

data class PaymentMethodUiState(
    val loading: Boolean = true,
    val system: List<PaymentMethod> = emptyList(),
    val custom: List<PaymentMethod> = emptyList(),
    val form: PaymentMethodForm? = null,
)

@HiltViewModel
class PaymentMethodViewModel @Inject constructor(private val repo: PaymentMethodRepository) : ViewModel() {
    private val form = MutableStateFlow<PaymentMethodForm?>(null)
    private val messages = Channel<SnackbarMessage>(Channel.BUFFERED)
    val snackbar: Flow<SnackbarMessage> = messages.receiveAsFlow()

    val uiState: StateFlow<PaymentMethodUiState> = combine(repo.observeAll(), form) { all, f ->
        PaymentMethodUiState(
            loading = false,
            system = all.filter { it.isSystem }.sortedBy { it.sortOrder },
            custom = all.filter { !it.isSystem }.sortedBy { it.sortOrder },
            form = f,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentMethodUiState())

    fun openAdd() = form.update { PaymentMethodForm(iconValue = "credit_card") }

    fun openEdit(method: PaymentMethod) = form.update {
        if (method.isSystem) null else PaymentMethodForm(id = method.id, name = method.name, iconValue = method.iconValue)
    }

    fun closeForm() = form.update { null }

    fun updateForm(transform: (PaymentMethodForm) -> PaymentMethodForm) = form.update { it?.let(transform)?.copy(error = null) }

    fun saveForm() {
        val f = form.value ?: return
        if (f.saving) return
        if (f.name.isBlank()) {
            form.update { it?.copy(error = UiText.res(R.string.money_invalid_blank_name)) }
            return
        }
        form.update { it?.copy(saving = true) }
        viewModelScope.launch {
            val result = if (f.id == null) {
                repo.add(f.name, f.iconValue ?: "credit_card")
            } else {
                repo.update(PaymentMethod(id = f.id, name = f.name, iconValue = f.iconValue ?: "credit_card"))
            }
            when (result) {
                is DataResult.Success -> {
                    form.value = null
                    messages.send(SnackbarMessage(UiText.res(R.string.money_pm_saved)))
                }
                is DataResult.Failure -> form.update { it?.copy(saving = false, error = result.error.toUiText()) }
            }
        }
    }

    fun delete(method: PaymentMethod) = viewModelScope.launch {
        when (val result = repo.delete(method.id)) {
            is DataResult.Success -> messages.send(SnackbarMessage(UiText.res(R.string.money_pm_deleted)))
            is DataResult.Failure -> messages.send(SnackbarMessage(result.error.toUiText()))
        }
    }

    fun resetToDefault() = viewModelScope.launch {
        when (val result = repo.resetToDefault()) {
            is DataResult.Success -> messages.send(SnackbarMessage(UiText.res(R.string.money_pm_reset_done)))
            is DataResult.Failure -> messages.send(SnackbarMessage(result.error.toUiText()))
        }
    }
}
