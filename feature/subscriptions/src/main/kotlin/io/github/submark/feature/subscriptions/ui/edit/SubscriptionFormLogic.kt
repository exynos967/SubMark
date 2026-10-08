package io.github.submark.feature.subscriptions.ui.edit

import io.github.submark.core.data.repository.CustomFieldRepository
import io.github.submark.core.data.repository.CustomFieldWithOptions
import io.github.submark.core.data.seed.SystemCategories
import io.github.submark.core.data.service.SubscriptionDraft
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.BundleRole
import io.github.submark.core.model.CycleUnit
import io.github.submark.core.model.IconType
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionPrefill
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.core.model.newId
import io.github.submark.core.ui.format.MoneyInput
import io.github.submark.core.ui.format.MoneyInputError
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What the kind selector offers. BUNDLE creates a REGULAR main subscription with children. */
enum class FormKind { REGULAR, STORED_VALUE, LIFETIME, WISHLIST, BUNDLE }

/** One bundle child being created alongside the main subscription. Currency and category follow the main. */
data class ChildForm(
    val key: String = newId(),
    val name: String = "",
    val priceText: String = "",
    val billingCycle: BillingCycle = BillingCycle.MONTHLY,
    val customCountText: String = "1",
    val customUnit: CycleUnit = CycleUnit.MONTH,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
)

/** Everything the add/edit form edits, as raw input values. */
data class SubscriptionForm(
    val name: String = "",
    val kind: FormKind = FormKind.REGULAR,
    val priceText: String = "",
    val currencyCode: String,
    val categoryId: String = SystemCategories.OTHER_ID,
    val iconType: IconType? = null,
    val iconValue: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val isSingleCycle: Boolean = false,
    val billingCycle: BillingCycle = BillingCycle.MONTHLY,
    val customCountText: String = "1",
    val customUnit: CycleUnit = CycleUnit.MONTH,
    val fixedPaymentDay: Int? = null,
    val renewalType: RenewalType = RenewalType.AUTO,
    val trialStartDate: LocalDate? = null,
    val trialDaysText: String = "7",
    val paymentMethodId: String? = null,
    val walletId: String? = null,
    val generateHistory: Boolean = true,
    val initialDepositText: String = "",
    val depositWalletId: String? = null,
    val tagIds: List<String> = emptyList(),
    val website: String = "",
    val note: String = "",
    val isActive: Boolean = true,
    /** fieldId -> encoded value. */
    val customFieldValues: Map<String, String> = emptyMap(),
    /** Not editable; kept from a prefill or the loaded subscription. */
    val appStoreId: String? = null,
    val originalPrice: BigDecimal? = null,
    val children: List<ChildForm> = emptyList(),
) {
    val isRecurring: Boolean get() = kind == FormKind.REGULAR || kind == FormKind.STORED_VALUE || kind == FormKind.BUNDLE
}

/** A reason the form can't be saved yet. */
sealed interface FormProblem {
    data object BlankName : FormProblem
    data class Price(val error: MoneyInputError) : FormProblem
    data object CustomCycle : FormProblem
    data object TrialStart : FormProblem
    data object TrialDays : FormProblem
    data object EndNotAfterStart : FormProblem
    data object SingleCycleNeedsEnd : FormProblem
    data object Deposit : FormProblem
    data object BundleNeedsChild : FormProblem
    data class ChildBlankName(val index: Int) : FormProblem
    data class ChildPrice(val index: Int) : FormProblem
    data class ChildCustomCycle(val index: Int) : FormProblem
    data class ChildEndNotAfterStart(val index: Int) : FormProblem
    data class FieldRequired(val fieldId: String, val name: String) : FormProblem
    data class FieldInvalid(val fieldId: String, val name: String) : FormProblem
}

/** Pure rules behind the add/edit form: validation, draft building, prefill and loading. */
object SubscriptionFormLogic {

    val MONTH_BASED = setOf(BillingCycle.MONTHLY, BillingCycle.QUARTERLY, BillingCycle.SEMIANNUALLY, BillingCycle.ANNUALLY)
    val TRIAL_PRESETS = listOf(3, 7, 14, 30)
    const val MAX_TRIAL_DAYS = 3650
    const val MAX_CUSTOM_COUNT = 1000

    fun fixedDayEligible(form: SubscriptionForm): Boolean =
        form.isRecurring && !form.isSingleCycle && form.billingCycle in MONTH_BASED

    /** Fixed day > 28 is clamped to the month end in short months. */
    fun fixedDayOverflows(day: Int?): Boolean = day != null && day > 28

    /** Start moved onto/past the end: keep the plan length (at least one day). Returns the new form and whether the end moved. */
    fun withStartDate(form: SubscriptionForm, start: LocalDate): Pair<SubscriptionForm, Boolean> {
        val end = form.endDate
        if (end == null || end > start) return form.copy(startDate = start) to false
        val length = maxOf(1L, ChronoUnit.DAYS.between(form.startDate, end))
        return form.copy(startDate = start, endDate = start.plusDays(length)) to true
    }

    fun endByDuration(start: LocalDate, amount: Int, months: Boolean): LocalDate =
        if (months) start.plusMonths(amount.toLong()) else start.plusDays(amount.toLong())

    /** Active fields shown for [categoryId]: global ones plus that category's, in order. */
    fun applicableFields(all: List<CustomFieldWithOptions>, categoryId: String): List<CustomFieldWithOptions> =
        all.filter { it.definition.isActive && (it.definition.categoryId == null || it.definition.categoryId == categoryId) }
            .sortedBy { it.definition.sortOrder }

    fun isFieldValueValid(field: CustomFieldWithOptions, value: String): Boolean =
        value.isBlank() || CustomFieldRepository.isValid(field.definition.type, value.trim(), field.options.map { it.id })

    fun validate(form: SubscriptionForm, fields: List<CustomFieldWithOptions>, isEdit: Boolean): List<FormProblem> = buildList {
        if (form.name.isBlank()) add(FormProblem.BlankName)
        val priceAllowsBlank = form.kind == FormKind.WISHLIST && form.priceText.isBlank()
        if (!priceAllowsBlank) {
            MoneyInput.validate(form.priceText, allowZero = true)?.let { add(FormProblem.Price(it)) }
        }
        if (form.isRecurring || form.kind == FormKind.WISHLIST) {
            if (form.billingCycle == BillingCycle.CUSTOM && !form.isSingleCycle && parseCount(form.customCountText) == null) add(FormProblem.CustomCycle)
        }
        if (form.isRecurring && form.renewalType == RenewalType.TRIAL) {
            if (form.trialStartDate == null) add(FormProblem.TrialStart)
            if (parseTrialDays(form.trialDaysText) == null) add(FormProblem.TrialDays)
        }
        val end = form.endDate
        if (end != null && end <= form.startDate) add(FormProblem.EndNotAfterStart)
        if (form.isRecurring && form.isSingleCycle && end == null) add(FormProblem.SingleCycleNeedsEnd)
        if (!isEdit && form.kind == FormKind.STORED_VALUE && form.initialDepositText.isNotBlank() &&
            MoneyInput.validate(form.initialDepositText, allowZero = true) != null
        ) {
            add(FormProblem.Deposit)
        }
        if (!isEdit && form.kind == FormKind.BUNDLE) {
            if (form.children.isEmpty()) add(FormProblem.BundleNeedsChild)
            form.children.forEachIndexed { i, child ->
                if (child.name.isBlank()) add(FormProblem.ChildBlankName(i))
                if (MoneyInput.validate(child.priceText, allowZero = true) != null) add(FormProblem.ChildPrice(i))
                if (child.billingCycle == BillingCycle.CUSTOM && parseCount(child.customCountText) == null) add(FormProblem.ChildCustomCycle(i))
                val childEnd = child.endDate
                if (childEnd != null && childEnd <= child.startDate) add(FormProblem.ChildEndNotAfterStart(i))
            }
        }
        applicableFields(fields, form.categoryId).forEach { field ->
            val value = form.customFieldValues[field.definition.id].orEmpty()
            if (field.definition.isRequired && value.isBlank()) {
                add(FormProblem.FieldRequired(field.definition.id, field.definition.name))
            } else if (!isFieldValueValid(field, value)) {
                add(FormProblem.FieldInvalid(field.definition.id, field.definition.name))
            }
        }
    }

    fun parseCount(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..MAX_CUSTOM_COUNT }

    fun parseTrialDays(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..MAX_TRIAL_DAYS }

    /**
     * Builds the service payload. [base] is the loaded subscription when editing, so fields the form
     * does not show (sharing, calendar, balance, timestamps...) are preserved. Assumes [validate] passed.
     */
    fun buildDraft(
        form: SubscriptionForm,
        base: Subscription?,
        fields: List<CustomFieldWithOptions>,
        today: LocalDate,
        now: Instant,
        allowDuplicate: Boolean = false,
    ): SubscriptionDraft {
        val isEdit = base != null
        val kind = when (form.kind) {
            FormKind.REGULAR, FormKind.BUNDLE -> SubscriptionKind.REGULAR
            FormKind.STORED_VALUE -> SubscriptionKind.STORED_VALUE
            FormKind.LIFETIME -> SubscriptionKind.LIFETIME
            FormKind.WISHLIST -> SubscriptionKind.WISHLIST
        }
        val recurring = form.isRecurring
        val cycle: BillingCycle? = when {
            kind == SubscriptionKind.LIFETIME -> null
            else -> form.billingCycle
        }
        val custom = cycle == BillingCycle.CUSTOM
        val trial = recurring && form.renewalType == RenewalType.TRIAL
        val price = MoneyInput.parse(form.priceText) ?: BigDecimal.ZERO
        val status = if (kind == SubscriptionKind.WISHLIST || !form.isActive) SubscriptionStatus.PAUSED else SubscriptionStatus.ACTIVE
        val template = base ?: Subscription(
            name = "", price = BigDecimal.ZERO, currencyCode = form.currencyCode, startDate = form.startDate,
            categoryId = form.categoryId, createdAt = now, updatedAt = now,
        )
        val subscription = template.copy(
            name = form.name.trim(),
            kind = kind,
            price = price,
            currencyCode = form.currencyCode,
            originalPrice = form.originalPrice ?: template.originalPrice,
            billingCycle = cycle,
            customCycleCount = if (custom) parseCount(form.customCountText) else null,
            customCycleUnit = if (custom) form.customUnit else null,
            isSingleCycle = recurring && form.isSingleCycle,
            fixedPaymentDay = form.fixedPaymentDay.takeIf { fixedDayEligible(form) },
            renewalType = when {
                trial -> RenewalType.TRIAL
                form.renewalType == RenewalType.TRIAL -> RenewalType.AUTO
                else -> form.renewalType
            },
            trialStartDate = if (trial) form.trialStartDate else null,
            trialDays = if (trial) parseTrialDays(form.trialDaysText) else null,
            startDate = form.startDate,
            endDate = form.endDate,
            status = status,
            categoryId = form.categoryId,
            iconType = form.iconType.takeIf { form.iconValue != null },
            iconValue = form.iconValue,
            website = form.website.trim().ifEmpty { null },
            appStoreId = form.appStoreId?.trim()?.ifEmpty { null },
            note = form.note.trim().ifEmpty { null },
            paymentMethodId = form.paymentMethodId,
            walletId = form.walletId.takeIf { kind != SubscriptionKind.WISHLIST },
            bundleRole = if (isEdit) template.bundleRole else if (form.kind == FormKind.BUNDLE) BundleRole.MAIN else BundleRole.NONE,
        )
        val values = applicableFields(fields, form.categoryId).associate { field ->
            field.definition.id to form.customFieldValues[field.definition.id]?.trim()?.ifEmpty { null }
        }
        val children = if (!isEdit && form.kind == FormKind.BUNDLE) {
            form.children.map { childDraft(it, subscription, now) }
        } else {
            emptyList()
        }
        return SubscriptionDraft(
            subscription = subscription,
            tagIds = form.tagIds.distinct(),
            customFieldValues = values,
            generateHistory = !isEdit && kind == SubscriptionKind.REGULAR && form.generateHistory && form.startDate < today,
            purchaseWalletId = form.walletId.takeIf { !isEdit && kind == SubscriptionKind.LIFETIME },
            initialDeposit = if (!isEdit && kind == SubscriptionKind.STORED_VALUE) {
                MoneyInput.parse(form.initialDepositText)?.takeIf { it.signum() > 0 }
            } else {
                null
            },
            initialDepositWalletId = form.depositWalletId.takeIf { !isEdit && kind == SubscriptionKind.STORED_VALUE },
            children = children,
            allowDuplicateAppStoreId = allowDuplicate,
        )
    }

    private fun childDraft(child: ChildForm, main: Subscription, now: Instant): SubscriptionDraft {
        val custom = child.billingCycle == BillingCycle.CUSTOM
        return SubscriptionDraft(
            subscription = Subscription(
                name = child.name.trim(),
                price = MoneyInput.parse(child.priceText) ?: BigDecimal.ZERO,
                currencyCode = main.currencyCode,
                billingCycle = child.billingCycle,
                customCycleCount = if (custom) parseCount(child.customCountText) else null,
                customCycleUnit = if (custom) child.customUnit else null,
                startDate = child.startDate,
                endDate = child.endDate,
                categoryId = main.categoryId,
                status = main.status,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** "Copy main dates": start, end and cycle of the main subscription. */
    fun copyMainDates(child: ChildForm, form: SubscriptionForm): ChildForm = child.copy(
        startDate = form.startDate,
        endDate = form.endDate,
        billingCycle = form.billingCycle,
        customCountText = form.customCountText,
        customUnit = form.customUnit,
    )

    fun newChild(form: SubscriptionForm): ChildForm = ChildForm(startDate = form.startDate, billingCycle = form.billingCycle)

    /** Loads an existing subscription into the form. */
    fun formFromSubscription(sub: Subscription, tagIds: List<String>, values: Map<String, String>): SubscriptionForm = SubscriptionForm(
        name = sub.name,
        kind = when (sub.kind) {
            SubscriptionKind.REGULAR -> FormKind.REGULAR
            SubscriptionKind.STORED_VALUE -> FormKind.STORED_VALUE
            SubscriptionKind.LIFETIME -> FormKind.LIFETIME
            SubscriptionKind.WISHLIST -> FormKind.WISHLIST
        },
        priceText = sub.price.stripTrailingZeros().toPlainString().let { if (it.contains('E')) sub.price.toPlainString() else it },
        currencyCode = sub.currencyCode,
        categoryId = sub.categoryId,
        iconType = sub.iconType,
        iconValue = sub.iconValue,
        startDate = sub.startDate,
        endDate = sub.endDate,
        isSingleCycle = sub.isSingleCycle,
        billingCycle = sub.billingCycle ?: BillingCycle.MONTHLY,
        customCountText = (sub.customCycleCount ?: 1).toString(),
        customUnit = sub.customCycleUnit ?: CycleUnit.MONTH,
        fixedPaymentDay = sub.fixedPaymentDay,
        renewalType = sub.renewalType,
        trialStartDate = sub.trialStartDate,
        trialDaysText = (sub.trialDays ?: 7).toString(),
        paymentMethodId = sub.paymentMethodId,
        walletId = sub.walletId,
        generateHistory = false,
        tagIds = tagIds,
        website = sub.website.orEmpty(),
        note = sub.note.orEmpty(),
        isActive = sub.status == SubscriptionStatus.ACTIVE,
        customFieldValues = values,
        appStoreId = sub.appStoreId,
        originalPrice = sub.originalPrice,
    )

    /**
     * Applies a prefill to a fresh form. [categoryIds] = ids of existing visible categories; an unknown or
     * hidden preset falls back to "Other". [tagIds] = ids already resolved for `prefill.tags`.
     */
    fun applyPrefill(form: SubscriptionForm, prefill: SubscriptionPrefill, categoryIds: Set<String>, tagIds: List<String>): SubscriptionForm {
        val categoryId = prefill.systemCategory?.let { SystemCategories.idOf(it) }?.takeIf { it in categoryIds }
            ?: if (prefill.systemCategory != null) SystemCategories.OTHER_ID else form.categoryId
        val start = prefill.startDate ?: form.startDate
        val kind = when {
            prefill.children.isNotEmpty() -> FormKind.BUNDLE
            else -> when (prefill.kind) {
                SubscriptionKind.STORED_VALUE -> FormKind.STORED_VALUE
                SubscriptionKind.LIFETIME -> FormKind.LIFETIME
                SubscriptionKind.WISHLIST -> FormKind.WISHLIST
                SubscriptionKind.REGULAR, null -> FormKind.REGULAR
            }
        }
        val base = form.copy(
            name = prefill.name ?: form.name,
            kind = kind,
            priceText = prefill.price?.toPlainString() ?: form.priceText,
            currencyCode = prefill.currencyCode ?: form.currencyCode,
            categoryId = categoryId,
            iconType = if (prefill.iconValue != null) prefill.iconType ?: IconType.URL else form.iconType,
            iconValue = prefill.iconValue ?: form.iconValue,
            website = prefill.website ?: form.website,
            appStoreId = prefill.appStoreId ?: form.appStoreId,
            note = prefill.note ?: form.note,
            startDate = start,
            endDate = prefill.endDate?.takeIf { it > start } ?: form.endDate,
            renewalType = prefill.renewalType?.takeIf { it != RenewalType.TRIAL } ?: form.renewalType,
            billingCycle = prefill.billingCycle ?: form.billingCycle,
            customCountText = prefill.customCycleCount?.toString() ?: form.customCountText,
            customUnit = prefill.customCycleUnit ?: form.customUnit,
            originalPrice = prefill.originalPrice ?: form.originalPrice,
            tagIds = (form.tagIds + tagIds).distinct(),
        )
        return base.copy(
            children = prefill.children.map { child ->
                ChildForm(
                    name = child.name.orEmpty(),
                    priceText = child.price?.toPlainString().orEmpty(),
                    billingCycle = child.billingCycle ?: base.billingCycle,
                    customCountText = child.customCycleCount?.toString() ?: "1",
                    customUnit = child.customCycleUnit ?: CycleUnit.MONTH,
                    startDate = child.startDate ?: start,
                    endDate = child.endDate?.takeIf { it > (child.startDate ?: start) },
                )
            },
        )
    }
}
