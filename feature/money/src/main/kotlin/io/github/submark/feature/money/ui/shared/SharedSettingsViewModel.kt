package io.github.submark.feature.money.ui.shared

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.service.SharedService
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.model.Currency
import io.github.submark.core.model.MemberStatus
import io.github.submark.core.model.SharedConfig
import io.github.submark.core.model.SharedMember
import io.github.submark.core.model.SplitMode
import io.github.submark.core.model.Subscription
import io.github.submark.core.ui.format.MoneyInput
import io.github.submark.core.ui.format.UiText
import io.github.submark.core.ui.navigation.SharedSettingsRoute
import io.github.submark.core.ui.util.SnackbarMessage
import io.github.submark.feature.money.R
import io.github.submark.feature.money.data.MoneyEnv
import io.github.submark.feature.money.data.MoneyEnvSource
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject

/** Member editor values; [id] null = new member. */
data class MemberDraft(
    val id: String? = null,
    val isCreator: Boolean = false,
    val name: String = "",
    val email: String = "",
    val note: String = "",
    val status: MemberStatus = MemberStatus.ACTIVE,
    val ratio: Float = 0f,
    val fixedText: String = "",
    val currencyCode: String = "",
    val joinedAt: LocalDate = LocalDate.MIN,
    val saving: Boolean = false,
    val error: UiText? = null,
) {
    val emailValid: Boolean get() = email.isBlank() || EMAIL.matches(email.trim())

    private companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

data class SharedSettingsUiState(
    val loading: Boolean = true,
    val subscription: Subscription? = null,
    val config: SharedConfig? = null,
    val members: List<SharedMember> = emptyList(),
    val preview: SplitPreview? = null,
    val draft: MemberDraft? = null,
    val currencies: List<Currency> = emptyList(),
    val busy: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    val env: MoneyEnv = MoneyEnv.EMPTY,
) {
    val enabled: Boolean get() = config != null
}

@HiltViewModel
class SharedSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    subscriptions: SubscriptionRepository,
    private val shared: SharedService,
    currencies: CurrencyRepository,
    envSource: MoneyEnvSource,
    private val time: TimeProvider,
) : ViewModel() {
    private val subscriptionId = savedStateHandle.toRoute<SharedSettingsRoute>().subscriptionId
    private val draft = MutableStateFlow<MemberDraft?>(null)
    private val busy = MutableStateFlow(false)
    private val messages = Channel<SnackbarMessage>(Channel.BUFFERED)
    val snackbar: Flow<SnackbarMessage> = messages.receiveAsFlow()

    val uiState: StateFlow<SharedSettingsUiState> = combine(
        combine(subscriptions.observe(subscriptionId), shared.observeConfig(subscriptionId), shared.observeMembers(subscriptionId), ::Triple),
        combine(draft, busy, ::Pair),
        currencies.observeEnabledSorted(),
        envSource.observe(),
    ) { (sub, config, members), (d, b), enabled, env ->
        SharedSettingsUiState(
            loading = false,
            subscription = sub,
            config = config,
            members = members,
            preview = if (sub != null && config != null) {
                SplitPreviewCalculator.compute(sub.price, sub.currencyCode, config.splitMode, members, env.converter)
            } else {
                null
            },
            draft = d,
            currencies = enabled,
            busy = b,
            today = time.today(),
            env = env,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SharedSettingsUiState())

    fun enable(creatorName: String) = run { shared.enable(subscriptionId, creatorName) }

    fun disable() = run(R.string.money_shared_disabled) { shared.disable(subscriptionId) }

    fun setSplitMode(mode: SplitMode) = run { shared.setSplitMode(subscriptionId, mode) }

    fun saveDescription(text: String) = run(R.string.money_shared_description_saved) { shared.updateDescription(subscriptionId, text) }

    fun deleteMember(member: SharedMember) = run(R.string.money_shared_member_deleted) { shared.deleteMember(subscriptionId, member.id) }

    fun newMember() {
        val sub = uiState.value.subscription ?: return
        draft.value = MemberDraft(currencyCode = sub.currencyCode, joinedAt = time.today())
    }

    fun editMember(member: SharedMember) {
        val sub = uiState.value.subscription ?: return
        draft.value = MemberDraft(
            id = member.id,
            isCreator = member.isCreator,
            name = member.name,
            email = member.email.orEmpty(),
            note = member.note.orEmpty(),
            status = member.status,
            ratio = member.ratioPercent?.toFloat() ?: 0f,
            fixedText = member.fixedAmount?.stripTrailingZeros()?.toPlainString().orEmpty(),
            currencyCode = member.paymentCurrencyCode ?: sub.currencyCode,
            joinedAt = member.joinedAt,
        )
    }

    fun updateDraft(transform: (MemberDraft) -> MemberDraft) = draft.update { it?.let(transform) }

    fun closeDraft() { draft.value = null }

    fun saveDraft() {
        val d = draft.value ?: return
        val state = uiState.value
        val sub = state.subscription ?: return
        if (d.saving) return
        val error = when {
            d.name.isBlank() -> UiText.res(R.string.money_invalid_blank_name)
            !d.emailValid -> UiText.res(R.string.money_shared_email_invalid)
            d.fixedText.isNotBlank() && MoneyInput.validate(d.fixedText, allowZero = true) != null -> UiText.res(R.string.money_invalid_negative_amount)
            else -> null
        }
        if (error != null) {
            draft.update { it?.copy(error = error) }
            return
        }
        val existing = state.members.firstOrNull { it.id == d.id }
        val now = time.now()
        val member = SharedMember(
            id = existing?.id ?: io.github.submark.core.model.newId(),
            subscriptionId = sub.id,
            name = d.name.trim(),
            email = d.email.trim().takeIf { it.isNotEmpty() },
            note = d.note.trim().takeIf { it.isNotEmpty() },
            status = d.status,
            isCreator = existing?.isCreator ?: false,
            ratioPercent = BigDecimal(d.ratio.toDouble()).setScale(2, RoundingMode.HALF_UP),
            fixedAmount = MoneyInput.parse(d.fixedText),
            paymentCurrencyCode = d.currencyCode.takeIf { it.isNotEmpty() && it != sub.currencyCode },
            joinedAt = d.joinedAt,
            sortOrder = existing?.sortOrder ?: 0,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        draft.update { it?.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = if (existing == null) shared.addMember(member) else shared.updateMember(member)
            when (result) {
                is DataResult.Success -> draft.value = null
                is DataResult.Failure -> draft.update { it?.copy(saving = false, error = result.error.toUiText()) }
            }
        }
    }

    private fun run(successMessage: Int? = null, block: suspend () -> DataResult<*>) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            when (val result = block()) {
                is DataResult.Success -> successMessage?.let { messages.send(SnackbarMessage(UiText.res(it))) }
                is DataResult.Failure -> messages.send(SnackbarMessage(result.error.toUiText()))
            }
            busy.value = false
        }
    }
}
