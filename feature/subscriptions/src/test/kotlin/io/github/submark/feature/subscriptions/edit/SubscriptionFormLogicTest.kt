package io.github.submark.feature.subscriptions.edit

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.data.repository.CustomFieldWithOptions
import io.github.submark.core.data.seed.SystemCategories
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.BundleRole
import io.github.submark.core.model.CustomFieldDefinition
import io.github.submark.core.model.CustomFieldOption
import io.github.submark.core.model.CustomFieldType
import io.github.submark.core.model.CycleUnit
import io.github.submark.core.model.IconType
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionPrefill
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.core.model.SystemCategory
import io.github.submark.core.ui.format.MoneyInputError
import io.github.submark.feature.subscriptions.ui.edit.ChildForm
import io.github.submark.feature.subscriptions.ui.edit.FormKind
import io.github.submark.feature.subscriptions.ui.edit.FormProblem
import io.github.submark.feature.subscriptions.ui.edit.SubscriptionForm
import io.github.submark.feature.subscriptions.ui.edit.SubscriptionFormLogic
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class SubscriptionFormLogicTest {
    private val today = LocalDate.of(2026, 3, 10)
    private val now = Instant.parse("2026-03-10T08:00:00Z")
    private val valid = SubscriptionForm(name = "Music", priceText = "9.99", currencyCode = "USD", startDate = today)

    private fun field(
        id: String,
        type: CustomFieldType,
        required: Boolean = false,
        categoryId: String? = null,
        active: Boolean = true,
        options: List<CustomFieldOption> = emptyList(),
    ) = CustomFieldWithOptions(
        CustomFieldDefinition(id = id, name = id, type = type, isRequired = required, categoryId = categoryId, isActive = active, createdAt = now),
        options,
    )

    @Test
    fun `valid form has no problems`() {
        assertThat(SubscriptionFormLogic.validate(valid, emptyList(), isEdit = false)).isEmpty()
    }

    @Test
    fun `blank name and bad price are reported`() {
        val problems = SubscriptionFormLogic.validate(valid.copy(name = "  ", priceText = ""), emptyList(), isEdit = false)
        assertThat(problems).containsExactly(FormProblem.BlankName, FormProblem.Price(MoneyInputError.EMPTY))
    }

    @Test
    fun `wishlist allows blank price and zero is allowed`() {
        assertThat(SubscriptionFormLogic.validate(valid.copy(kind = FormKind.WISHLIST, priceText = ""), emptyList(), false)).isEmpty()
        assertThat(SubscriptionFormLogic.validate(valid.copy(priceText = "0"), emptyList(), false)).isEmpty()
    }

    @Test
    fun `custom cycle, trial, end date and single cycle are validated`() {
        val form = valid.copy(
            billingCycle = BillingCycle.CUSTOM,
            customCountText = "0",
            renewalType = RenewalType.TRIAL,
            trialStartDate = null,
            trialDaysText = "",
            endDate = today,
        )
        assertThat(SubscriptionFormLogic.validate(form, emptyList(), false)).containsExactly(
            FormProblem.CustomCycle, FormProblem.TrialStart, FormProblem.TrialDays, FormProblem.EndNotAfterStart,
        )
        val single = valid.copy(isSingleCycle = true, endDate = null)
        assertThat(SubscriptionFormLogic.validate(single, emptyList(), false)).containsExactly(FormProblem.SingleCycleNeedsEnd)
    }

    @Test
    fun `custom fields are required and format checked only when applicable`() {
        val fields = listOf(
            field("email", CustomFieldType.EMAIL),
            field("req", CustomFieldType.TEXT, required = true),
            field("other", CustomFieldType.TEXT, required = true, categoryId = "cat_x"),
            field("off", CustomFieldType.TEXT, required = true, active = false),
        )
        val problems = SubscriptionFormLogic.validate(valid.copy(customFieldValues = mapOf("email" to "nope")), fields, false)
        assertThat(problems).containsExactly(FormProblem.FieldInvalid("email", "email"), FormProblem.FieldRequired("req", "req"))
    }

    @Test
    fun `bundle needs valid children`() {
        val bundle = valid.copy(kind = FormKind.BUNDLE)
        assertThat(SubscriptionFormLogic.validate(bundle, emptyList(), false)).containsExactly(FormProblem.BundleNeedsChild)
        val child = ChildForm(name = "", priceText = "x", startDate = today, endDate = today.minusDays(1))
        assertThat(SubscriptionFormLogic.validate(bundle.copy(children = listOf(child)), emptyList(), false)).containsExactly(
            FormProblem.ChildBlankName(0), FormProblem.ChildPrice(0), FormProblem.ChildEndNotAfterStart(0),
        )
    }

    @Test
    fun `regular draft maps form fields and history only for past start`() {
        val form = valid.copy(
            startDate = today.minusMonths(3),
            fixedPaymentDay = 15,
            tagIds = listOf("t1", "t1", "t2"),
            website = " example.com ",
            note = "",
            renewalType = RenewalType.MANUAL,
            trialStartDate = today,
        )
        val draft = SubscriptionFormLogic.buildDraft(form, base = null, fields = emptyList(), today = today, now = now)
        val sub = draft.subscription
        assertThat(sub.kind).isEqualTo(SubscriptionKind.REGULAR)
        assertThat(sub.price).isEqualTo(BigDecimal("9.99"))
        assertThat(sub.fixedPaymentDay).isEqualTo(15)
        assertThat(sub.renewalType).isEqualTo(RenewalType.MANUAL)
        assertThat(sub.trialStartDate).isNull()
        assertThat(sub.website).isEqualTo("example.com")
        assertThat(sub.note).isNull()
        assertThat(sub.status).isEqualTo(SubscriptionStatus.ACTIVE)
        assertThat(draft.tagIds).containsExactly("t1", "t2").inOrder()
        assertThat(draft.generateHistory).isTrue()
        val future = SubscriptionFormLogic.buildDraft(valid.copy(startDate = today.plusDays(1)), null, emptyList(), today, now)
        assertThat(future.generateHistory).isFalse()
    }

    @Test
    fun `custom cycle and trial are carried`() {
        val form = valid.copy(
            billingCycle = BillingCycle.CUSTOM, customCountText = "10", customUnit = CycleUnit.DAY,
            renewalType = RenewalType.TRIAL, trialStartDate = today, trialDaysText = "14",
        )
        val sub = SubscriptionFormLogic.buildDraft(form, null, emptyList(), today, now).subscription
        assertThat(sub.customCycleCount).isEqualTo(10)
        assertThat(sub.customCycleUnit).isEqualTo(CycleUnit.DAY)
        assertThat(sub.trialDays).isEqualTo(14)
        assertThat(sub.renewalType).isEqualTo(RenewalType.TRIAL)
    }

    @Test
    fun `lifetime draft has no cycle and charges the purchase wallet`() {
        val form = valid.copy(kind = FormKind.LIFETIME, walletId = "w1", fixedPaymentDay = 3, isSingleCycle = true)
        val draft = SubscriptionFormLogic.buildDraft(form, null, emptyList(), today, now)
        assertThat(draft.subscription.kind).isEqualTo(SubscriptionKind.LIFETIME)
        assertThat(draft.subscription.billingCycle).isNull()
        assertThat(draft.subscription.fixedPaymentDay).isNull()
        assertThat(draft.subscription.isSingleCycle).isFalse()
        assertThat(draft.purchaseWalletId).isEqualTo("w1")
        assertThat(draft.generateHistory).isFalse()
    }

    @Test
    fun `stored value draft carries the initial deposit only when adding`() {
        val form = valid.copy(kind = FormKind.STORED_VALUE, initialDepositText = "50", depositWalletId = "w2")
        val draft = SubscriptionFormLogic.buildDraft(form, null, emptyList(), today, now)
        assertThat(draft.subscription.kind).isEqualTo(SubscriptionKind.STORED_VALUE)
        assertThat(draft.initialDeposit).isEqualTo(BigDecimal("50"))
        assertThat(draft.initialDepositWalletId).isEqualTo("w2")
        val base = Subscription(name = "x", price = BigDecimal.ONE, currencyCode = "USD", startDate = today, categoryId = "c", createdAt = now, updatedAt = now)
        val edit = SubscriptionFormLogic.buildDraft(form, base, emptyList(), today, now)
        assertThat(edit.initialDeposit).isNull()
        assertThat(edit.initialDepositWalletId).isNull()
    }

    @Test
    fun `wishlist draft is paused and has no wallet`() {
        val form = valid.copy(kind = FormKind.WISHLIST, priceText = "", isActive = true, walletId = "w")
        val sub = SubscriptionFormLogic.buildDraft(form, null, emptyList(), today, now).subscription
        assertThat(sub.kind).isEqualTo(SubscriptionKind.WISHLIST)
        assertThat(sub.status).isEqualTo(SubscriptionStatus.PAUSED)
        assertThat(sub.price).isEqualTo(BigDecimal.ZERO)
        assertThat(sub.walletId).isNull()
    }

    @Test
    fun `bundle draft creates children with main currency and category`() {
        val child = ChildForm(name = " Video ", priceText = "5", billingCycle = BillingCycle.ANNUALLY, startDate = today.plusDays(2))
        val form = valid.copy(kind = FormKind.BUNDLE, currencyCode = "EUR", categoryId = "cat_video", children = listOf(child))
        val draft = SubscriptionFormLogic.buildDraft(form, null, emptyList(), today, now)
        assertThat(draft.subscription.bundleRole).isEqualTo(BundleRole.MAIN)
        assertThat(draft.children).hasSize(1)
        val c = draft.children.single().subscription
        assertThat(c.name).isEqualTo("Video")
        assertThat(c.currencyCode).isEqualTo("EUR")
        assertThat(c.categoryId).isEqualTo("cat_video")
        assertThat(c.billingCycle).isEqualTo(BillingCycle.ANNUALLY)
        assertThat(c.price).isEqualTo(BigDecimal("5"))
    }

    @Test
    fun `edit preserves fields the form does not show`() {
        val created = Instant.parse("2025-01-01T00:00:00Z")
        val base = Subscription(
            id = "s1", name = "Old", price = BigDecimal.ONE, currencyCode = "USD", startDate = today.minusYears(1), categoryId = "c",
            appStoreId = "123", isShared = true, customReminderEnabled = true, calendarSyncEnabled = true, calendarEventId = 7,
            storedValueBalance = BigDecimal("12"), bundleRole = BundleRole.MAIN, createdAt = created, updatedAt = created,
        )
        val form = SubscriptionFormLogic.formFromSubscription(base, listOf("t"), mapOf("f" to "v")).copy(name = "New")
        val draft = SubscriptionFormLogic.buildDraft(form, base, emptyList(), today, now)
        val sub = draft.subscription
        assertThat(sub.id).isEqualTo("s1")
        assertThat(sub.name).isEqualTo("New")
        assertThat(sub.appStoreId).isEqualTo("123")
        assertThat(sub.isShared).isTrue()
        assertThat(sub.customReminderEnabled).isTrue()
        assertThat(sub.calendarSyncEnabled).isTrue()
        assertThat(sub.calendarEventId).isEqualTo(7)
        assertThat(sub.storedValueBalance).isEqualTo(BigDecimal("12"))
        assertThat(sub.bundleRole).isEqualTo(BundleRole.MAIN)
        assertThat(sub.createdAt).isEqualTo(created)
        assertThat(draft.generateHistory).isFalse()
        assertThat(draft.children).isEmpty()
    }

    @Test
    fun `only applicable custom field values are sent and blanks clear`() {
        val fields = listOf(field("g", CustomFieldType.TEXT), field("c", CustomFieldType.TEXT, categoryId = "other"))
        val form = valid.copy(customFieldValues = mapOf("g" to "  ", "c" to "x"))
        val draft = SubscriptionFormLogic.buildDraft(form, null, fields, today, now)
        assertThat(draft.customFieldValues).containsExactly("g", null)
    }

    @Test
    fun `fixed day eligibility`() {
        assertThat(SubscriptionFormLogic.fixedDayEligible(valid)).isTrue()
        assertThat(SubscriptionFormLogic.fixedDayEligible(valid.copy(billingCycle = BillingCycle.WEEKLY))).isFalse()
        assertThat(SubscriptionFormLogic.fixedDayEligible(valid.copy(billingCycle = BillingCycle.CUSTOM))).isFalse()
        assertThat(SubscriptionFormLogic.fixedDayEligible(valid.copy(isSingleCycle = true))).isFalse()
        assertThat(SubscriptionFormLogic.fixedDayEligible(valid.copy(kind = FormKind.LIFETIME))).isFalse()
        val draft = SubscriptionFormLogic.buildDraft(valid.copy(billingCycle = BillingCycle.WEEKLY, fixedPaymentDay = 5), null, emptyList(), today, now)
        assertThat(draft.subscription.fixedPaymentDay).isNull()
        assertThat(SubscriptionFormLogic.fixedDayOverflows(29)).isTrue()
        assertThat(SubscriptionFormLogic.fixedDayOverflows(28)).isFalse()
    }

    @Test
    fun `moving start past end keeps the plan length`() {
        val form = valid.copy(endDate = today.plusDays(30))
        val (moved, adjusted) = SubscriptionFormLogic.withStartDate(form, today.plusDays(40))
        assertThat(adjusted).isTrue()
        assertThat(moved.endDate).isEqualTo(today.plusDays(70))
        val (kept, notAdjusted) = SubscriptionFormLogic.withStartDate(form, today.plusDays(5))
        assertThat(notAdjusted).isFalse()
        assertThat(kept.endDate).isEqualTo(today.plusDays(30))
        assertThat(SubscriptionFormLogic.endByDuration(LocalDate.of(2026, 1, 31), 1, months = true)).isEqualTo(LocalDate.of(2026, 2, 28))
    }

    @Test
    fun `prefill maps category, kind, tags and children`() {
        val prefill = SubscriptionPrefill(
            name = "Suite", price = BigDecimal("20"), currencyCode = "EUR", systemCategory = SystemCategory.VIDEO,
            iconValue = "https://x/icon.png", appStoreId = "42", tags = listOf("a"),
            children = listOf(SubscriptionPrefill(name = "Part", price = BigDecimal("3"), billingCycle = BillingCycle.ANNUALLY)),
        )
        val visible = setOf(SystemCategories.idOf(SystemCategory.VIDEO), SystemCategories.OTHER_ID)
        val form = SubscriptionFormLogic.applyPrefill(valid.copy(name = ""), prefill, visible, listOf("tagA"))
        assertThat(form.kind).isEqualTo(FormKind.BUNDLE)
        assertThat(form.categoryId).isEqualTo("cat_video")
        assertThat(form.currencyCode).isEqualTo("EUR")
        assertThat(form.iconType).isEqualTo(IconType.URL)
        assertThat(form.appStoreId).isEqualTo("42")
        assertThat(form.tagIds).containsExactly("tagA")
        assertThat(form.children.single().name).isEqualTo("Part")
        assertThat(form.children.single().billingCycle).isEqualTo(BillingCycle.ANNUALLY)
    }

    @Test
    fun `prefill with hidden category falls back to other and maps lifetime`() {
        val prefill = SubscriptionPrefill(systemCategory = SystemCategory.GAMING, kind = SubscriptionKind.LIFETIME)
        val form = SubscriptionFormLogic.applyPrefill(valid, prefill, setOf(SystemCategories.OTHER_ID), emptyList())
        assertThat(form.categoryId).isEqualTo(SystemCategories.OTHER_ID)
        assertThat(form.kind).isEqualTo(FormKind.LIFETIME)
        assertThat(form.name).isEqualTo("Music")
    }

    @Test
    fun `copy main dates copies start end and cycle`() {
        val main = valid.copy(endDate = today.plusMonths(6), billingCycle = BillingCycle.QUARTERLY)
        val child = SubscriptionFormLogic.copyMainDates(ChildForm(name = "c", startDate = today.minusDays(9)), main)
        assertThat(child.startDate).isEqualTo(today)
        assertThat(child.endDate).isEqualTo(today.plusMonths(6))
        assertThat(child.billingCycle).isEqualTo(BillingCycle.QUARTERLY)
    }

    @Test
    fun `dropdown values must be a known option`() {
        val dd = field("d", CustomFieldType.DROPDOWN, options = listOf(CustomFieldOption(id = "o1", fieldId = "d", label = "One")))
        assertThat(SubscriptionFormLogic.isFieldValueValid(dd, "o1")).isTrue()
        assertThat(SubscriptionFormLogic.isFieldValueValid(dd, "zzz")).isFalse()
        assertThat(SubscriptionFormLogic.isFieldValueValid(dd, "")).isTrue()
    }
}
