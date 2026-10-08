package io.github.submark.feature.integrations.panel.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.ApiServiceType
import io.github.submark.core.ui.navigation.ApiBudgetEditRoute
import io.github.submark.feature.integrations.panel.data.ApiBudgetForm
import io.github.submark.feature.integrations.panel.data.DuplicateServiceTypeException
import io.github.submark.feature.integrations.panel.data.PanelErrorReason
import io.github.submark.feature.integrations.panel.data.PanelFetchException
import io.github.submark.feature.integrations.panel.data.PanelRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class BudgetEditUiState(
    val loading: Boolean = true,
    val id: String? = null,
    val serviceType: ApiServiceType = ApiServiceType.DEEPSEEK,
    val name: String = "",
    val apiKey: String = "",
    val hasStoredKey: Boolean = false,
    val baseUrl: String = "",
    val userId: String = "",
    val currencyCode: String = "USD",
    val monthlyBudget: String = "",
    val dailyEnabled: Boolean = false,
    val dailyBudget: String = "",
    val alertThreshold: Int = 90,
    val enabled: Boolean = true,
    val saving: Boolean = false,
    val testing: Boolean = false,
    val fieldError: BudgetFieldError? = null,
) {
    val isEdit: Boolean get() = id != null

    fun form(): ApiBudgetForm = ApiBudgetForm(
        id = id,
        name = name,
        serviceType = serviceType,
        apiKey = apiKey,
        baseUrl = baseUrl.ifBlank { null },
        userId = userId.ifBlank { null },
        currencyCode = currencyCode,
        monthlyBudget = monthlyBudget.toBigDecimalOrNull(),
        dailyBudget = if (dailyEnabled) dailyBudget.toBigDecimalOrNull() else null,
        alertThreshold = alertThreshold,
        enabled = enabled,
    )
}

enum class BudgetFieldError { NAME_REQUIRED, KEY_REQUIRED, BASE_URL_REQUIRED }

sealed interface BudgetEditEvent {
    data object Saved : BudgetEditEvent
    data class Message(val reason: PanelErrorReason) : BudgetEditEvent
    data object TestSuccess : BudgetEditEvent
    data object DuplicateType : BudgetEditEvent
}

@HiltViewModel
class BudgetEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PanelRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val route: ApiBudgetEditRoute = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(BudgetEditUiState())
    val state: StateFlow<BudgetEditUiState> = _state.asStateFlow()

    private val _events = Channel<BudgetEditEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val id = route.id
            _state.value = if (id == null) {
                BudgetEditUiState(loading = false)
            } else {
                val config = repository.getBudget(id)
                val key = repository.budgetSecret(id)
                if (config == null) {
                    _events.send(BudgetEditEvent.Saved) // gone; close
                    return@launch
                }
                BudgetEditUiState(
                    loading = false,
                    id = config.id,
                    serviceType = config.serviceType,
                    name = config.name,
                    apiKey = "",
                    hasStoredKey = !key.isNullOrBlank(),
                    baseUrl = config.baseUrl.orEmpty(),
                    userId = config.userId.orEmpty(),
                    currencyCode = config.currencyCode,
                    monthlyBudget = config.monthlyBudget?.stripTrailingZeros()?.toPlainString() ?: "",
                    dailyEnabled = config.dailyBudget != null,
                    dailyBudget = config.dailyBudget?.stripTrailingZeros()?.toPlainString() ?: "",
                    alertThreshold = config.alertThreshold,
                    enabled = config.enabled,
                )
            }
        }
    }

    fun update(transform: (BudgetEditUiState) -> BudgetEditUiState) {
        _state.value = transform(_state.value).copy(fieldError = null)
    }

    fun testConnection() {
        val s = _state.value
        val error = validate(s) ?: run {
            if (s.apiKey.isBlank() && !s.hasStoredKey) BudgetFieldError.KEY_REQUIRED else null
        }
        if (error != null) {
            _state.value = s.copy(fieldError = error)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(testing = true)
            try {
                repository.testBudget(s.form())
                _events.send(BudgetEditEvent.TestSuccess)
            } catch (e: PanelFetchException) {
                _events.send(BudgetEditEvent.Message(e.reason))
            } catch (e: Exception) {
                _events.send(BudgetEditEvent.Message(PanelErrorReason.UNKNOWN))
            } finally {
                _state.value = _state.value.copy(testing = false)
            }
        }
    }

    fun save() {
        val s = _state.value
        val error = validate(s)
        if (error != null) {
            _state.value = s.copy(fieldError = error)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            try {
                repository.saveBudget(s.form())
                _events.send(BudgetEditEvent.Saved)
            } catch (e: DuplicateServiceTypeException) {
                _events.send(BudgetEditEvent.DuplicateType)
            } finally {
                _state.value = _state.value.copy(saving = false)
            }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            repository.deleteBudget(id)
            _events.send(BudgetEditEvent.Saved)
        }
    }

    private fun validate(s: BudgetEditUiState): BudgetFieldError? = when {
        s.name.isBlank() -> BudgetFieldError.NAME_REQUIRED
        !s.isEdit && s.apiKey.isBlank() -> BudgetFieldError.KEY_REQUIRED
        s.serviceType == ApiServiceType.NEWAPI && s.baseUrl.isBlank() -> BudgetFieldError.BASE_URL_REQUIRED
        s.serviceType == ApiServiceType.NEWAPI && !isValidHttpUrl(s.baseUrl) -> BudgetFieldError.BASE_URL_REQUIRED
        s.serviceType != ApiServiceType.NEWAPI && s.baseUrl.isNotBlank() && !isValidHttpUrl(s.baseUrl) ->
            BudgetFieldError.BASE_URL_REQUIRED
        else -> null
    }

    private fun isValidHttpUrl(url: String): Boolean =
        url.startsWith("https://") || url.startsWith("http://")

    companion object {
        val THRESHOLDS = listOf(50, 75, 90, 95)
    }
}
